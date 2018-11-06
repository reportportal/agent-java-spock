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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getFirst;
import static org.spockframework.runtime.model.MethodKind.*;

import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.*;
import org.spockframework.runtime.model.MethodKind;
import org.spockframework.util.ExceptionUtil;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.ListenersUtils;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.restclient.endpoint.exception.RestEndpointIOException;
import com.epam.reportportal.service.IReportPortalService;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * Default implementation of {@link ISpockReporter}, which posts test results to
 * the RP using the {@link com.epam.reportportal.service.IReportPortalService}
 * instance
 *
 * @author Dzmitry Mikhievich
 */
class SpockReporter implements ISpockReporter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpockReporter.class);

	// stores the bindings of Spock method kinds to the RP-specific notation
	private static final Map<MethodKind, String> ITEM_TYPES_REGISTRY = ImmutableMap.<MethodKind, String> builder()
			.put(SPEC_EXECUTION, "SUITE").put(SETUP_SPEC, "BEFORE_CLASS").put(SETUP, "BEFORE_METHOD").put(FEATURE, "TEST")
			.put(CLEANUP, "AFTER_METHOD").put(CLEANUP_SPEC, "AFTER_CLASS").build();

	private final AtomicBoolean rpIsDown = new AtomicBoolean(false);

	private final IReportPortalService reportPortalService;
	private final ListenerParameters launchParameters;
	private final AbstractLaunchContext launchContext;

	@Inject
	SpockReporter(IReportPortalService reportPortalService, ListenerParameters parameters, AbstractLaunchContext launchContext) {
		checkArgument(reportPortalService != null, "Report portal service should't be null");
		checkArgument(parameters != null, "Listener parameters shouldn't be null");
		checkArgument(launchContext != null, "Null launch context is passed");

		this.reportPortalService = reportPortalService;
		this.launchParameters = parameters;
		this.launchContext = launchContext;
	}

	@Override
	public void startLaunch() {
		if (launchContext.tryStartLaunch()) {
			StartLaunchRQ startLaunchRQ = createStartLaunchRQ();
			try {
				EntryCreatedRS response = reportPortalService.startLaunch(startLaunchRQ);
				launchContext.setLaunchId(response.getId());
			} catch (RestEndpointIOException ex) {
				handleRpException(ex, "Unable start the launch: '" + launchParameters.getLaunchName() + "'");
			}
		}
	}

	@Override
	public void registerFixture(MethodInfo fixture) {
		if (rpIsDown.get()) {
			return;
		}

		NodeFootprint specFootprint = launchContext.findSpecFootprint(fixture.getParent());
		boolean isFixtureInherited = !fixture.getParent().equals(specFootprint.getItem());
		String fixtureDisplayName = getFixtureDisplayName(fixture, isFixtureInherited);
		MethodKind kind = fixture.getKind();
		StartTestItemRQ rq = createBaseStartTestItemRQ(fixtureDisplayName, ITEM_TYPES_REGISTRY.get(kind));
		try {
			EntryCreatedRS rs = reportPortalService.startTestItem(specFootprint.getId(), rq);
			NodeFootprint fixtureOwnerFootprint = findFixtureOwner(fixture);
			fixtureOwnerFootprint.addFixtureFootprint(new FixtureFootprint(fixture, rs.getId()));
			DisorderedListenerContextDelegate.setRunningNowItemId(rs.getId());
		} catch (RestEndpointIOException ex) {
			handleRpException(ex, "Unable to start '" + fixtureDisplayName + "' fixture");
		}
	}

	@Override
	public void registerSpec(SpecInfo spec) {
		if (rpIsDown.get() || launchContext.isSpecRegistered(spec)) {
			return;
		}

		StartTestItemRQ rq = createBaseStartTestItemRQ(spec.getName(), ITEM_TYPES_REGISTRY.get(SPEC_EXECUTION));
		rq.setDescription(spec.getNarrative());
		try {
			EntryCreatedRS rs = reportPortalService.startRootTestItem(rq);
			launchContext.addRunningSpec(rs.getId(), spec);
		} catch (RestEndpointIOException ex) {
			handleRpException(ex, "Unable start spec: '" + spec.getName() + "'");
		}
	}

	@Override
	public void registerFeature(FeatureInfo feature) {
		if (rpIsDown.get()) {
			return;
		}

		launchContext.addRunningFeature(feature);
		if (isMonolithicParametrizedFeature(feature) && !feature.isSkipped()) {
			IterationInfo maskedIteration = buildIterationMaskForFeature(feature);
			reportIterationStart(maskedIteration);
		}
	}

	@Override
	public void registerIteration(IterationInfo iteration) {
		if (rpIsDown.get() || isMonolithicParametrizedFeature(iteration.getFeature())) {
			return;
		}
		reportIterationStart(iteration);
	}

	@Override
	public void trackSkippedFeature(FeatureInfo featureInfo) {
		if (rpIsDown.get()) {
			return;
		}

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
		if (rpIsDown.get()) {
			return;
		}

		registerSpec(spec);
		ReportableItemFootprint specFootprint = launchContext.findSpecFootprint(spec);
		specFootprint.setStatus(SKIPPED);
	}

	@Override
	public void publishFixtureResult(MethodInfo fixture) {
		if (rpIsDown.get()) {
			return;
		}

		NodeFootprint ownerFootprint = findFixtureOwner(fixture);
		ReportableItemFootprint fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(fixture);
		reportTestItemFinish(fixtureFootprint);
	}

	@Override
	public void publishSpecResult(SpecInfo spec) {
		if (rpIsDown.get()) {
			return;
		}

		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		reportTestItemFinish(specFootprint);
	}

	@Override
	public void publishIterationResult(IterationInfo iteration) {
		if (rpIsDown.get()) {
			return;
		}

		FeatureInfo feature = iteration.getFeature();
		if (!isMonolithicParametrizedFeature(feature)) {
			ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
			reportTestItemFinish(footprint);
		}
	}

	@Override
	public void publishFeatureResult(FeatureInfo feature) {
		if (rpIsDown.get()) {
			return;
		}

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
		if (rpIsDown.get()) {
			return;
		}

		MethodInfo errorSource = error.getMethod();
		SpecInfo sourceSpec = errorSource.getParent();
		MethodKind errorSourceKind = errorSource.getKind();
		ReportableItemFootprint errorSourceFootprint = null;

		if (FEATURE.equals(errorSourceKind)) {
			IterationInfo iterationInfo = launchContext.getRuntimePointerForSpec(sourceSpec).getCurrentIteration();
			errorSourceFootprint = launchContext.findIterationFootprint(iterationInfo);

		} else if (SHARED_INITIALIZER.equals(errorSourceKind)) {
			/*
			 * Explicitly register specification here, because in the case of
			 * shared initializer error appropriate listener method isn't
			 * triggered
			 */
			registerSpec(sourceSpec);
			errorSourceFootprint = launchContext.findSpecFootprint(sourceSpec);

		} else if (DATA_PROCESSOR.equals(errorSourceKind) || INITIALIZER.equals(errorSourceKind)) {
			FeatureInfo featureInfo = launchContext.getRuntimePointerForSpec(sourceSpec).getCurrentFeature();
			IterationInfo maskedIteration = buildIterationMaskForFeature(featureInfo);
			registerIteration(maskedIteration);
			errorSourceFootprint = launchContext.findIterationFootprint(maskedIteration);

		} else if (errorSourceKind.isSpecScopedFixtureMethod()) {
			NodeFootprint originalSpecFootprint = launchContext.findSpecFootprint(sourceSpec);
			errorSourceFootprint = originalSpecFootprint.findFixtureFootprint(errorSource);

		} else if (errorSourceKind.isFeatureScopedFixtureMethod()) {
			IterationInfo runningIteration = launchContext.getRuntimePointerForSpec(sourceSpec).getCurrentIteration();
			NodeFootprint originalIterationFootprint = launchContext.findIterationFootprint(runningIteration);
			errorSourceFootprint = originalIterationFootprint.findFixtureFootprint(errorSource);
		} else {
			LOGGER.warn("Unable to handle error of type {}", errorSourceKind);
		}

		if (IS_NOT_PUBLISHED.apply(errorSourceFootprint)) {
			errorSourceFootprint.setStatus(FAILED);
			reportTestItemFailure(errorSourceFootprint, error);
		}
	}

	@Override
	public void finishLaunch() {
		if (launchContext.tryFinishLaunch()) {
			// publish all registered unpublished specifications first
			for (NodeFootprint<SpecInfo> footprint : launchContext.findAllUnpublishedSpecFootprints()) {
				reportTestItemFinish(footprint);
			}
			// finish launch
			FinishExecutionRQ rq = createFinishExecutionRQ();
			try {
				reportPortalService.finishLaunch(launchContext.getLaunchId(), rq);
			} catch (RestEndpointIOException ex) {
				handleRpException(ex, "Unable finish the launch: '" + launchContext.getLaunchId() + "'");
			}
		}
	}

	@VisibleForTesting
	void reportIterationStart(IterationInfo iteration) {
		FeatureInfo feature = iteration.getFeature();
		StartTestItemRQ rq = createBaseStartTestItemRQ(iteration.getName(), ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setDescription(buildFeatureDescription(feature));
		ReportableItemFootprint specFootprint = launchContext.findSpecFootprint(feature.getSpec());
		try {
			EntryCreatedRS rs = reportPortalService.startTestItem(specFootprint.getId(), rq);
			launchContext.addRunningIteration(rs.getId(), iteration);
			DisorderedListenerContextDelegate.setRunningNowItemId(rs.getId());
		} catch (RestEndpointIOException ex) {
			handleRpException(ex, "Unable start test method: '" + iteration.getName() + "'");
		}
	}

	@VisibleForTesting
	void reportTestItemFinish(ReportableItemFootprint<?> footprint) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		rq.setStatus(calculateFootprintStatus(footprint));
		try {
			reportPortalService.finishTestItem(footprint.getId(), rq);
			DisorderedListenerContextDelegate.setRunningNowItemId(null);
		} catch (RestEndpointIOException ex) {
			handleRpException(ex, "Unable finish " + footprint.getClass().getSimpleName() + ": '" + footprint.getItemName() + "'");
		} finally {
			footprint.markAsPublished();
		}
	}

	@VisibleForTesting
	void reportTestItemFailure(ReportableItemFootprint testItemFootprint, ErrorInfo errorInfo) {
		SaveLogRQ saveLogRQ = new SaveLogRQ();
		saveLogRQ.setLogTime(Calendar.getInstance().getTime());
		saveLogRQ.setTestItemId(testItemFootprint.getId());
		saveLogRQ.setMessage("Exception: " + ExceptionUtil.printStackTrace(errorInfo.getException()));
		saveLogRQ.setLevel("ERROR");
		try {
			reportPortalService.log(saveLogRQ);
		} catch (RestEndpointIOException ex) {
			handleRpException(ex, "Unable to send message to Report Portal");
		}
	}

	@VisibleForTesting
	void handleRpException(RestEndpointIOException rpException, String message) {
		rpIsDown.set(true);
		ListenersUtils.handleException(rpException, LOGGER, message);
	}

	@VisibleForTesting
	NodeFootprint findFixtureOwner(MethodInfo fixture) {
		MethodKind kind = fixture.getKind();
		SpecInfo sourceSpec = fixture.getParent();
		if (kind.isSpecScopedFixtureMethod()) {
			return launchContext.findSpecFootprint(sourceSpec);
		} else {
			IterationInfo currentIteration = launchContext.getRuntimePointerForSpec(sourceSpec).getCurrentIteration();
			return launchContext.findIterationFootprint(currentIteration);
		}
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
		startLaunchRQ.setName(launchParameters.getLaunchName());
		startLaunchRQ.setDescription(launchParameters.getDescription());
		startLaunchRQ.setStartTime(Calendar.getInstance().getTime());
		startLaunchRQ.setTags(launchParameters.getTags());
		startLaunchRQ.setMode(launchParameters.getMode());

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

	@VisibleForTesting
	static String calculateFootprintStatus(ReportableItemFootprint<?> footprint) {
		Optional<String> footprintStatus = footprint.getStatus();
		if (footprintStatus.isPresent()) {
			return footprintStatus.get();
		}
		// don't set status explicitly for footprints with descendants:
		// delegate status calculation to RP
		return footprint.hasDescendants() ? null : Statuses.PASSED;

	}

	@VisibleForTesting
	static boolean isMonolithicParametrizedFeature(FeatureInfo feature) {
		return feature.isParameterized() && !feature.isReportIterations();
	}

}
