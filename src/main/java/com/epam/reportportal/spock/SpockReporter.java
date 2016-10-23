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

import static com.epam.reportportal.listeners.Statuses.*;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_PUBLISHED_CONDITION;
import static com.google.common.collect.Iterables.filter;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.*;
import org.spockframework.util.ExceptionUtil;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.ListenersUtils;
import com.epam.reportportal.listeners.ReportPortalListenerContext;
import com.epam.reportportal.restclient.endpoint.exception.RestEndpointIOException;
import com.epam.reportportal.service.IReportPortalService;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.Mode;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;

/**
 * @author Dzmitry Mikhievich
 */
class SpockReporter implements ISpockReporter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SpockReporter.class);

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

	@Override
	public void registerSpec(SpecInfo spec) {
		StartTestItemRQ rq = createBaseStartTestItemRQ(spec.getName(), "TEST");
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setDescription(spec.getNarrative());
		try {
			EntryCreatedRS rs = reportPortalService.startRootTestItem(rq);
			launchContext.addRunningSpec(rs.getId(), spec);
			ReportPortalListenerContext.setRunningNowItemId(rs.getId());
		} catch (Exception e) {
			String message = "Unable start spec: '" + spec.getName() + "'";
			ListenersUtils.handleException(e, LOGGER, message);
		}
	}

	@Override
	public void registerFeature(FeatureInfo featureInfo) {
		launchContext.addRunningFeature(featureInfo);
	}

	@Override
	public void registerIteration(IterationInfo iteration) {
		StartTestItemRQ rq = createBaseStartTestItemRQ(iteration.getName(), "STEP");
		rq.setDescription(NodeInfoUtils.getFeatureDescription(iteration.getFeature()));
		ReportableItemFootprint spec = launchContext.findSpecFootprint(iteration.getFeature().getSpec());
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

	@Override
	public void trackSkippedFeature(FeatureInfo featureInfo) {
		IterationInfo maskedIteration = buildIterationMaskOfFeature(featureInfo);
		registerIteration(maskedIteration);
		// set skipped status in an appropriate footprint
		ReportableItemFootprint footprint = launchContext.findIterationFootprint(maskedIteration);
		footprint.setStatus(SKIPPED);
		// publish result of masked iteration
		publishIterationResult(maskedIteration);
	}

	@Override
	public void trackSkippedSpec(SpecInfo spec) {
		ReportableItemFootprint specFootprint = launchContext.findSpecFootprint(spec);
		specFootprint.setStatus(SKIPPED);
	}

	@Override
	public void publishSpecResult(SpecInfo spec) {
		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		rq.setStatus(specFootprint.getStatus().orNull());
		try {
			reportPortalService.finishTestItem(specFootprint.getId(), rq);
		} catch (RestEndpointIOException exception) {
			String errorMessage = "Unable finish the launch: '" + launchContext.getLaunchId() + "'";
			ListenersUtils.handleException(exception, LOGGER, errorMessage);
		} finally {
			specFootprint.markAsPublished();
		}
	}

	@Override
	public void publishIterationResult(IterationInfo iteration) {
		ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
		ReportPortalListenerContext.setRunningNowItemId(null);
		reportIterationResult(footprint);
	}

	@Override
	public void publishFeatureResult(FeatureInfo feature) {
		List<? extends ReportableItemFootprint<IterationInfo>> iterations = launchContext.findIterationFootprints(feature);
		Iterable<? extends ReportableItemFootprint<IterationInfo>> nonPublishedIterations = filter(iterations, IS_PUBLISHED_CONDITION);
		for (ReportableItemFootprint<IterationInfo> iterationFootprint : nonPublishedIterations) {
			reportIterationResult(iterationFootprint);
		}
	}

	@Override
	public void reportError(ErrorInfo error) {

		MethodInfo errorSource = error.getMethod();

		switch (errorSource.getKind()) {

		case SHARED_INITIALIZER:
		case SETUP_SPEC:
			SpecInfo specInfo = launchContext.getRuntimePointer().getCurrentSpec();
			ReportableItemFootprint specFootprint = launchContext.findSpecFootprint(specInfo);
			specFootprint.setStatus(FAILED);
			reportTestItemFailure(specFootprint, error);
			break;

		case DATA_PROCESSOR:
		case INITIALIZER:
			FeatureInfo featureInfo = launchContext.getRuntimePointer().getCurrentFeature();
			IterationInfo maskedIteration = buildIterationMaskOfFeature(featureInfo);
			registerIteration(maskedIteration);
			ReportableItemFootprint maskedIterationFootprint = launchContext.findIterationFootprint(maskedIteration);
			maskedIterationFootprint.setStatus(FAILED);
			reportTestItemFailure(maskedIterationFootprint, error);
			break;

		case SETUP:
		case FEATURE:
		case CLEANUP:
			IterationInfo iterationInfo = launchContext.getRuntimePointer().getCurrentIteration();
			ReportableItemFootprint iterationFootprint = launchContext.findIterationFootprint(iterationInfo);
			iterationFootprint.setStatus(FAILED);
			reportTestItemFailure(iterationFootprint, error);
			break;

		default:
			LOGGER.warn("Unable to handle error of type {}", errorSource.getKind());

			// case CLEANUP_SPEC:
		}
	}

	@Override
	public void finishLaunch() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		try {
			reportPortalService.finishLaunch(launchContext.getLaunchId(), rq);
		} catch (RestEndpointIOException exception) {
			String errorMessage = "Unable finish the launch: '" + launchContext.getLaunchId() + "'";
			ListenersUtils.handleException(exception, LOGGER, errorMessage);
		}
	}

	private void reportIterationResult(ReportableItemFootprint<IterationInfo> footprint) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		rq.setStatus(footprint.getStatus().or(PASSED));
		try {
			reportPortalService.finishTestItem(footprint.getId(), rq);
		} catch (Exception e) {
			String message = "Unable finish iteration: '" + footprint.getNodeInfo().getName() + "'";
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

	private IterationInfo buildIterationMaskOfFeature(FeatureInfo featureInfo) {
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
}
