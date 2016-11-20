/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/agent-java-spock
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.spock;

import static com.epam.reportportal.listeners.Statuses.FAILED;
import static com.epam.reportportal.listeners.Statuses.SKIPPED;
import static com.epam.reportportal.spock.NodeInfoUtils.buildFeatureDescription;
import static com.epam.reportportal.spock.NodeInfoUtils.getFixtureDisplayName;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getFirst;
import static org.spockframework.runtime.model.MethodKind.*;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.*;
import org.spockframework.util.ExceptionUtil;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.ListenersUtils;
import com.epam.reportportal.listeners.ReportPortalListenerContext;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.restclient.endpoint.exception.RestEndpointIOException;
import com.epam.reportportal.service.IReportPortalService;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.Mode;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * Default implementation of {@link ISpockReporter}, which posts test results to the RP using the
 * {@link com.epam.reportportal.service.IReportPortalService} instance
 *
 * @author Dzmitry Mikhievich
 */
class SpockReporter implements ISpockReporter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpockReporter.class);

	//stores the bindings of Spock method kinds to the RP-specific notation
	private static final Map<MethodKind, String> ITEM_TYPES_REGISTRY = ImmutableMap.<MethodKind, String> builder()
			.put(SPEC_EXECUTION, "TEST").put(SETUP_SPEC, "BEFORE_CLASS").put(SETUP, "BEFORE_METHOD").put(FEATURE, "STEP")
			.put(CLEANUP, "AFTER_METHOD").put(CLEANUP_SPEC, "AFTER_CLASS").build();

	private final IReportPortalService reportPortalService;

	private final Set<String> launchTags;
	private final String launchName;
	private final String launchDescription;
	private final Mode launchRunningMode;
	private final AbstractLaunchContext launchContext;

	@Inject
	public SpockReporter(IReportPortalService reportPortalService, ListenerParameters parameters, AbstractLaunchContext launchContext) {
		this.reportPortalService = reportPortalService;
		this.launchName = parameters.getLaunchName();
		this.launchDescription = parameters.getDescription();
		this.launchTags = parameters.getTags();
		this.launchRunningMode = parameters.getMode();
		this.launchContext = launchContext;
	}

	@Override
	public void startLaunch() {
		StartLaunchRQ startLaunchRQ = createStartLaunchRQ();
		try {
			EntryCreatedRS response = reportPortalService.startLaunch(startLaunchRQ);
			launchContext.setLaunchId(response.getId());
		} catch (RestEndpointIOException exception) {
			String errorMessage = "Unable start the launch: '" + launchName + "'";
			ListenersUtils.handleException(exception, LOGGER, errorMessage);
		}
	}

	@Override
	public void registerFixture(MethodInfo fixture) {
		NodeFootprint specFootprint = launchContext.findSpecFootprint(fixture.getParent());
		boolean isFixtureInherited = !fixture.getParent().equals(specFootprint.getItem());
		String fixtureDisplayName = getFixtureDisplayName(fixture, isFixtureInherited);
		MethodKind kind = fixture.getKind();
		StartTestItemRQ rq = createBaseStartTestItemRQ(fixtureDisplayName, ITEM_TYPES_REGISTRY.get(kind));
		try {
			EntryCreatedRS rs = reportPortalService.startTestItem(specFootprint.getId(), rq);
			NodeFootprint fixtureOwnerFootprint;
			if (kind.isSpecScopedFixtureMethod()) {
				fixtureOwnerFootprint = specFootprint;
			} else {
				IterationInfo currentIteration = launchContext.getRuntimePointer().getCurrentIteration();
				fixtureOwnerFootprint = launchContext.findIterationFootprint(currentIteration);
			}
			fixtureOwnerFootprint.addFixtureFootprint(new FixtureFootprint(fixture, rs.getId()));
			ReportPortalListenerContext.setRunningNowItemId(rs.getId());
		} catch (Exception e) {
			String message = "Unable to start '" + fixtureDisplayName + "' fixture";
			ListenersUtils.handleException(e, LOGGER, message);
		}
	}

	@Override
	public void registerSpec(SpecInfo spec) {
		if (launchContext.isSpecRegistered(spec)) {
			return;
		}
		StartTestItemRQ rq = createBaseStartTestItemRQ(spec.getName(), ITEM_TYPES_REGISTRY.get(SPEC_EXECUTION));
		rq.setDescription(spec.getNarrative());
		try {
			EntryCreatedRS rs = reportPortalService.startRootTestItem(rq);
			launchContext.addRunningSpec(rs.getId(), spec);
		} catch (Exception e) {
			String message = "Unable start spec: '" + spec.getName() + "'";
			ListenersUtils.handleException(e, LOGGER, message);
		}
	}

	@Override
	public void registerFeature(FeatureInfo feature) {
		launchContext.addRunningFeature(feature);
		if (isMonolithicParametrizedFeature(feature) && !feature.isSkipped()) {
			IterationInfo maskedIteration = buildIterationMaskForFeature(feature);
			reportIterationStart(maskedIteration);
		}
	}

	@Override
	public void registerIteration(IterationInfo iteration) {
		if (!isMonolithicParametrizedFeature(iteration.getFeature())) {
			reportIterationStart(iteration);
		}
	}

	@Override
	public void trackSkippedFeature(FeatureInfo featureInfo) {
		IterationInfo maskedIteration = buildIterationMaskForFeature(featureInfo);
		reportIterationStart(maskedIteration);
		// set skipped status in an appropriate footprint
		ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(maskedIteration);
		footprint.setStatus(SKIPPED);
		// report result of masked iteration
		reportTestItemFinish(footprint);
	}

	@Override
	public void trackSkippedSpec(SpecInfo spec) {
		registerSpec(spec);
		ReportableItemFootprint specFootprint = launchContext.findSpecFootprint(spec);
		specFootprint.setStatus(SKIPPED);
	}

	@Override
	public void publishFixtureResult(MethodInfo fixture) {
		MethodKind kind = fixture.getKind();
		NodeFootprint ownerFootprint;
		if (kind.isSpecScopedFixtureMethod()) {
			ownerFootprint = launchContext.findSpecFootprint(fixture.getParent());
		} else {
			IterationInfo currentIteration = launchContext.getRuntimePointer().getCurrentIteration();
			ownerFootprint = launchContext.findIterationFootprint(currentIteration);
		}
		ReportableItemFootprint fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(fixture);
		reportTestItemFinish(fixtureFootprint);
	}

	@Override
	public void publishSpecResult(SpecInfo spec) {
		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		reportTestItemFinish(specFootprint);
	}

	@Override
	public void publishIterationResult(IterationInfo iteration) {
		FeatureInfo feature = iteration.getFeature();
		if (!isMonolithicParametrizedFeature(feature)) {
			ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
			reportTestItemFinish(footprint);
		}
	}

	@Override
	public void publishFeatureResult(FeatureInfo feature) {
		if (isMonolithicParametrizedFeature(feature)) {
			ReportableItemFootprint<IterationInfo> footprint = getFirst(launchContext.findIterationFootprints(feature), null);
			reportTestItemFinish(footprint);
		} else {
			Iterable<? extends ReportableItemFootprint<IterationInfo>> iterations = launchContext.findIterationFootprints(feature);
			Iterable<? extends ReportableItemFootprint<IterationInfo>> nonPublishedIterations = filter(iterations, IS_NOT_PUBLISHED);
			for (ReportableItemFootprint<IterationInfo> iterationFootprint : nonPublishedIterations) {
				reportTestItemFinish(iterationFootprint);
			}
		}
	}

	@Override
	public void reportError(ErrorInfo error) {
		MethodInfo errorSource = error.getMethod();
		MethodKind errorSourceKind = errorSource.getKind();
		ReportableItemFootprint errorSourceFootprint = null;

		if (FEATURE.equals(errorSourceKind)) {
			IterationInfo iterationInfo = launchContext.getRuntimePointer().getCurrentIteration();
			errorSourceFootprint = launchContext.findIterationFootprint(iterationInfo);

		} else if (SHARED_INITIALIZER.equals(errorSourceKind)) {
			SpecInfo sourceSpec = errorSource.getParent();
			/*
			 * Explicitly register specification here, because in the case of
			 * shared initializer error appropriate listener method isn't
			 * triggered
			 */
			registerSpec(sourceSpec);
			errorSourceFootprint = launchContext.findSpecFootprint(sourceSpec);

		} else if (DATA_PROCESSOR.equals(errorSourceKind) || INITIALIZER.equals(errorSourceKind)) {
			FeatureInfo featureInfo = launchContext.getRuntimePointer().getCurrentFeature();
			IterationInfo maskedIteration = buildIterationMaskForFeature(featureInfo);
			registerIteration(maskedIteration);
			errorSourceFootprint = launchContext.findIterationFootprint(maskedIteration);

		} else if (errorSourceKind.isSpecScopedFixtureMethod()) {
			NodeFootprint originalSpecFootprint = launchContext.findSpecFootprint(errorSource.getParent());
			errorSourceFootprint = originalSpecFootprint.findFixtureFootprint(errorSource);

		} else if (errorSourceKind.isFeatureScopedFixtureMethod()) {
			IterationInfo runningIteration = launchContext.getRuntimePointer().getCurrentIteration();
			NodeFootprint originalIterationFootprint = launchContext.findIterationFootprint(runningIteration);
			errorSourceFootprint = originalIterationFootprint.findFixtureFootprint(errorSource);
		} else {
			LOGGER.warn("Unable to handle error of type {}", errorSourceKind);
		}

		if (errorSourceFootprint != null && !errorSourceFootprint.isPublished()) {
			errorSourceFootprint.setStatus(FAILED);
			reportTestItemFailure(errorSourceFootprint, error);
		}
	}

	@Override
	public void finishLaunch() {
		// publish all registered unpublished specifications first
		for (NodeFootprint<SpecInfo> footprint : launchContext.findAllUnpublishedSpecFootprints()) {
			reportTestItemFinish(footprint);
		}
		// finish launch
		FinishExecutionRQ rq = createFinishExecutionRQ();
		try {
			reportPortalService.finishLaunch(launchContext.getLaunchId(), rq);
		} catch (RestEndpointIOException exception) {
			String errorMessage = "Unable finish the launch: '" + launchContext.getLaunchId() + "'";
			ListenersUtils.handleException(exception, LOGGER, errorMessage);
		}
	}

	private void reportIterationStart(IterationInfo iteration) {
		FeatureInfo feature = iteration.getFeature();
		StartTestItemRQ rq = createBaseStartTestItemRQ(iteration.getName(), ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setDescription(buildFeatureDescription(feature));
		ReportableItemFootprint spec = launchContext.findSpecFootprint(feature.getSpec());
		String specId = spec != null ? spec.getId() : null;
		try {
			EntryCreatedRS rs = reportPortalService.startTestItem(specId, rq);
			launchContext.addRunningIteration(rs.getId(), iteration);
			ReportPortalListenerContext.setRunningNowItemId(rs.getId());
		} catch (Exception e) {
			String message = "Unable start test method: '" + iteration.getName() + "'";
			ListenersUtils.handleException(e, LOGGER, message);
		}
	}

	private void reportTestItemFinish(ReportableItemFootprint<?> footprint) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		rq.setStatus(calculateFootprintStatus(footprint));
		try {
			reportPortalService.finishTestItem(footprint.getId(), rq);
			if (!footprint.hasDescendants()) {
				ReportPortalListenerContext.setRunningNowItemId(null);
			}
		} catch (Exception e) {
			String message = "Unable finish " + footprint.getClass().getSimpleName() + ": '" + footprint.getItemName() + "'";
			ListenersUtils.handleException(e, LOGGER, message);
		} finally {
			footprint.markAsPublished();
		}
	}

	private void reportTestItemFailure(ReportableItemFootprint testItemFootprint, ErrorInfo errorInfo) {
		SaveLogRQ saveLogRQ = new SaveLogRQ();
		saveLogRQ.setLogTime(Calendar.getInstance().getTime());
		saveLogRQ.setTestItemId(testItemFootprint.getId());
		saveLogRQ.setMessage("Exception: " + ExceptionUtil.printStackTrace(errorInfo.getException()));
		saveLogRQ.setLevel("ERROR");
		try {
			reportPortalService.log(saveLogRQ);
		} catch (Exception e1) {
			ListenersUtils.handleException(e1, LOGGER, "Unable to send message to Report Portal");
		}
	}

	private static String calculateFootprintStatus(ReportableItemFootprint<?> footprint) {
		Optional<String> footprintStatus = footprint.getStatus();
		if (footprintStatus.isPresent()) {
			return footprintStatus.get();
		}
		// don't set status explicitly for footprints with descendants:
		// delegate status calculation to RP
		return footprint.hasDescendants() ? null : Statuses.PASSED;

	}

	private IterationInfo buildIterationMaskForFeature(FeatureInfo featureInfo) {
		IterationInfo iterationInfo = new IterationInfo(featureInfo, null, 0);
		iterationInfo.setName(featureInfo.getName());
		iterationInfo.setDescription(featureInfo.getDescription());
		return iterationInfo;
	}

	private FinishExecutionRQ createFinishExecutionRQ() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		return rq;
	}

	private StartLaunchRQ createStartLaunchRQ() {
		StartLaunchRQ startLaunchRQ = new StartLaunchRQ();
		startLaunchRQ.setName(launchName);
		startLaunchRQ.setDescription(launchDescription);
		startLaunchRQ.setStartTime(Calendar.getInstance().getTime());
		startLaunchRQ.setTags(launchTags);
		startLaunchRQ.setMode(launchRunningMode);

		return startLaunchRQ;
	}

	private StartTestItemRQ createBaseStartTestItemRQ(String name, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		rq.setLaunchId(launchContext.getLaunchId());
		return rq;
	}

	private static boolean isMonolithicParametrizedFeature(FeatureInfo feature) {
		return feature.isParameterized() && !feature.isReportIterations();
	}
}
