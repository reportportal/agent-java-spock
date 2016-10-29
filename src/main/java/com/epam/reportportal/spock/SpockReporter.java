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
import static com.epam.reportportal.spock.NodeInfoUtils.retrieveSpecName;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_PUBLISHED_CONDITION;
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
 * @author Dzmitry Mikhievich
 */
class SpockReporter implements ISpockReporter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpockReporter.class);

	private static final Map<MethodKind, String> ITEM_TYPES_REGISTRY = ImmutableMap.<MethodKind, String> builder()
			.put(SPEC_EXECUTION, "TEST").put(SETUP_SPEC, "BEFORE_CLASS").put(SETUP, "BEFORE_METHOD").put(FEATURE, "STEP")
			.put(CLEANUP, "AFTER_METHOD").put(CLEANUP_SPEC, "AFTER_CLASS").build();

	private final IReportPortalService reportPortalService;

	private final Set<String> launchTags;
	private final String launchName;
	private final Mode launchRunningMode;
	private final AbstractLaunchContext launchContext;

	@Inject
	public SpockReporter(IReportPortalService reportPortalService, ListenerParameters parameters, AbstractLaunchContext launchContext) {

		this.reportPortalService = reportPortalService;
		this.launchName = parameters.getLaunchName();
		this.launchTags = parameters.getTags();
		this.launchRunningMode = parameters.getMode();
		this.launchContext = launchContext;
	}

	@Override
	public void startLaunch() {
		StartLaunchRQ startLaunchRQ = new StartLaunchRQ();
		startLaunchRQ.setName(launchName);
		startLaunchRQ.setStartTime(Calendar.getInstance().getTime());
		startLaunchRQ.setTags(launchTags);
		startLaunchRQ.setMode(launchRunningMode);

		EntryCreatedRS response;
		try {
			response = reportPortalService.startLaunch(startLaunchRQ);
			launchContext.setLaunchId(response.getId());
		} catch (RestEndpointIOException exception) {
			String errorMessage = "Unable start the launch: '" + launchName + "'";
			ListenersUtils.handleException(exception, LOGGER, errorMessage);
		}
	}

	// TODO add impl
	@Override
	public void registerFixture(MethodInfo fixture) {
		MethodKind kind = fixture.getKind();
		NodeFootprint specFootprint = launchContext.findSpecFootprint(fixture.getParent());
		StartTestItemRQ rq = createBaseStartTestItemRQ(fixture.getName(), ITEM_TYPES_REGISTRY.get(kind));
		try {
			EntryCreatedRS rs = reportPortalService.startTestItem(specFootprint.getId(), rq);
			NodeFootprint fixtureOwnerFootprint = specFootprint;
			if (kind.isFeatureScopedFixtureMethod()) {
				IterationInfo currentIteration = launchContext.getRuntimePointer().getCurrentIteration();
				fixtureOwnerFootprint = launchContext.findIterationFootprint(currentIteration);
			}
			fixtureOwnerFootprint.addFixtureFootprint(new FixtureFootprint(fixture, rs.getId()));
			ReportPortalListenerContext.setRunningNowItemId(rs.getId());
		} catch (Exception e) {
			String message = "Unable to start " + kind + " fixture";
			ListenersUtils.handleException(e, LOGGER, message);
		}
	}

	@Override
	public void registerSpec(SpecInfo spec) {
		if (launchContext.isSpecRegistered(spec)) {
			return;
		}
		StartTestItemRQ rq = createBaseStartTestItemRQ(retrieveSpecName(spec), ITEM_TYPES_REGISTRY.get(SPEC_EXECUTION));
		rq.setDescription(NodeInfoUtils.retrieveSpecNarrative(spec));
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
		if (isNotUnrolledParametrizedFeature(feature) && !feature.isSkipped()) {
			IterationInfo maskedIteration = buildIterationMaskForFeature(feature);
			reportIterationStart(maskedIteration);
		}
	}

	@Override
	public void registerIteration(IterationInfo iteration) {
		if (!isNotUnrolledParametrizedFeature(iteration.getFeature())) {
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
		FixtureFootprint fixtureFootprint = ownerFootprint.findFixtureFootprint(fixture, Boolean.FALSE);
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
		if (!isNotUnrolledParametrizedFeature(feature)) {
			ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
			reportTestItemFinish(footprint);
		}
	}

	@Override
	public void publishFeatureResult(FeatureInfo feature) {
		if (isNotUnrolledParametrizedFeature(feature)) {
			ReportableItemFootprint<IterationInfo> footprint = getFirst(launchContext.findIterationFootprints(feature), null);
			reportTestItemFinish(footprint);
		} else {
			Iterable<? extends ReportableItemFootprint<IterationInfo>> iterations = launchContext.findIterationFootprints(feature);
			Iterable<? extends ReportableItemFootprint<IterationInfo>> nonPublishedIterations = filter(iterations, IS_PUBLISHED_CONDITION);
			for (ReportableItemFootprint<IterationInfo> iterationFootprint : nonPublishedIterations) {
				reportTestItemFinish(iterationFootprint);
			}
		}
	}

	@Override
	public void reportError(ErrorInfo error) {

		MethodInfo errorSource = error.getMethod();
		switch (errorSource.getKind()) {

		case SHARED_INITIALIZER:
			SpecInfo specInfo = launchContext.getRuntimePointer().getCurrentSpec();
			NodeFootprint specFootprint = launchContext.findSpecFootprint(specInfo);
			specFootprint.setStatus(FAILED);
			reportTestItemFailure(specFootprint, error);
			break;

		case SETUP_SPEC:
		case CLEANUP_SPEC:
			NodeFootprint originalSpecFootprint = launchContext.findSpecFootprint(errorSource.getParent());
			FixtureFootprint specFixtureFootprint = originalSpecFootprint.findFixtureFootprint(errorSource, null);
			if (!specFixtureFootprint.isPublished()) {
				specFixtureFootprint.setStatus(FAILED);
				// originalSpecFootprint.setStatus(FAILED);
				reportTestItemFailure(specFixtureFootprint, error);
			}
			break;

		case DATA_PROCESSOR:
		case INITIALIZER:
			FeatureInfo featureInfo = launchContext.getRuntimePointer().getCurrentFeature();
			IterationInfo maskedIteration = buildIterationMaskForFeature(featureInfo);
			registerIteration(maskedIteration);
			NodeFootprint maskedIterationFootprint = launchContext.findIterationFootprint(maskedIteration);
			maskedIterationFootprint.setStatus(FAILED);
			reportTestItemFailure(maskedIterationFootprint, error);
			break;

		case SETUP:
		case CLEANUP:
			IterationInfo runningIteration = launchContext.getRuntimePointer().getCurrentIteration();
			NodeFootprint originalIterationFootprint = launchContext.findIterationFootprint(runningIteration);
			FixtureFootprint iterationFixtureFootprint = originalIterationFootprint.findFixtureFootprint(errorSource, null);
			if (!iterationFixtureFootprint.isPublished()) {
				iterationFixtureFootprint.setStatus(FAILED);
				reportTestItemFailure(iterationFixtureFootprint, error);
			}
			break;

		case FEATURE:
			IterationInfo iterationInfo = launchContext.getRuntimePointer().getCurrentIteration();
			NodeFootprint iterationFootprint = launchContext.findIterationFootprint(iterationInfo);
			iterationFootprint.setStatus(FAILED);
			reportTestItemFailure(iterationFootprint, error);
			break;

		default:
			LOGGER.warn("Unable to handle error of type {}", errorSource.getKind());
		}
	}

	@Override
	public void finishLaunch() {
		// publish all registered unpublished specifications first
		for (NodeFootprint<SpecInfo> footprint : launchContext.findAllUnpublishedSpecFootprints()) {
			reportTestItemFinish(footprint);
		}
		// finish launch
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
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
		} else if (footprint.hasDescendants()) {
			// don't set status explicitly for footprints with descendants:
			// delegate status calculation to RP
			return null;
		}
		return Statuses.PASSED;
	}

	private IterationInfo buildIterationMaskForFeature(FeatureInfo featureInfo) {
		IterationInfo iterationInfo = new IterationInfo(featureInfo, null, 0);
		iterationInfo.setName(featureInfo.getName());
		return iterationInfo;
	}

	private StartTestItemRQ createBaseStartTestItemRQ(String name, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		rq.setLaunchId(launchContext.getLaunchId());
		return rq;
	}

	private static boolean isNotUnrolledParametrizedFeature(FeatureInfo feature) {
		return feature.isParameterized() && !feature.isReportIterations();
	}
}
