/*
 * Copyright (C) 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.spock;

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.exception.ReportPortalException;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.StatusEvaluation;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.model.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static com.epam.reportportal.listeners.ItemStatus.*;
import static com.epam.reportportal.spock.NodeInfoUtils.*;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Optional.ofNullable;
import static org.spockframework.runtime.model.MethodKind.*;

/**
 * Backward-compatible version of Listeners with version prior to 3.0.0
 * Allows to have as many listener instances as needed.
 * The best approach is to have only one instance
 */
public class ReportPortalSpockListener extends AbstractRunListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalSpockListener.class);
	private final MemoizingSupplier<Launch> launch;

	// stores the bindings of Spock method kinds to the RP-specific notation
	private static final Map<MethodKind, String> ITEM_TYPES_REGISTRY = ImmutableMap.<MethodKind, String>builder()
			.put(SPEC_EXECUTION, "TEST")
			.put(SETUP_SPEC, "BEFORE_CLASS")
			.put(SETUP, "BEFORE_METHOD")
			.put(FEATURE, "STEP")
			.put(CLEANUP, "AFTER_METHOD")
			.put(CLEANUP_SPEC, "AFTER_CLASS")
			.build();

	private ListenerParameters launchParameters;
	private final AbstractLaunchContext launchContext;
	private Maybe<String> registeredFeatureId = null;

	public ReportPortalSpockListener() {
		this(ReportPortal.builder().build());
	}

	public ReportPortalSpockListener(final ReportPortal reportPortal) {
		launchContext = new LaunchContextImpl();
		this.launch = new MemoizingSupplier<>(() -> {
			launchParameters = reportPortal.getParameters();
			StartLaunchRQ rq = createStartLaunchRQ();

			rq.setStartTime(Calendar.getInstance().getTime());
			return reportPortal.newLaunch(rq);
		});
	}

	public ReportPortalSpockListener(Supplier<Launch> launch) {
		checkArgument(launch != null, "launch shouldn't be null");
		launchContext = new LaunchContextImpl();
		this.launch = new MemoizingSupplier<>(launch);
	}

	public ReportPortalSpockListener(Supplier<Launch> launch, AbstractLaunchContext launchContext) {
		checkArgument(launch != null, "launch shouldn't be null");
		this.launchContext = launchContext;
		this.launch = new MemoizingSupplier<>(launch);
	}

	public void startLaunch() {
		if (launchContext.tryStartLaunch()) {
			try {
				Maybe<String> launchId = this.launch.get().start();
				launchContext.setLaunchId(launchId);
			} catch (ReportPortalException ex) {
				handleRpException(ex, "Unable start the launch: '" + launchParameters.getLaunchName() + "'");
			}
		}
	}

	public void registerSpec(SpecInfo spec) {
		if (launchContext.isSpecRegistered(spec)) {
			return;
		}

		StartTestItemRQ rq = createBaseStartTestItemRQ(spec.getName(), ITEM_TYPES_REGISTRY.get(SPEC_EXECUTION));
		rq.setDescription(spec.getNarrative());
		rq.setCodeRef(spec.getDescription().getClassName());
		Maybe<String> testItemId = this.launch.get().startTestItem(rq);
		launchContext.addRunningSpec(testItemId, spec);
	}

	public void registerFixture(SpecInfo spec, FeatureInfo feature, IterationInfo iteration, MethodInfo fixture) {
		NodeFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		boolean isFixtureInherited = !fixture.getParent().equals(specFootprint.getItem());
		String fixtureDisplayName = getFixtureDisplayName(fixture, isFixtureInherited);
		MethodKind kind = fixture.getKind();
		StartTestItemRQ rq = createBaseStartTestItemRQ(fixtureDisplayName, ITEM_TYPES_REGISTRY.get(kind));
		Maybe<String> testItemId;
		if (kind.isFeatureScopedFixtureMethod() && !feature.isReportIterations() && feature.isParameterized()) {
			rq.setHasStats(false);
			testItemId = launch.get().startTestItem(launchContext.findFeatureFootprint(feature).getId(), rq);
		} else {
			testItemId = launch.get().startTestItem(specFootprint.getId(), rq);
		}
		@SuppressWarnings("rawtypes")
		NodeFootprint<? extends NodeInfo> fixtureOwnerFootprint = findFixtureOwner(feature, iteration, fixture);
		fixtureOwnerFootprint.addFixtureFootprint(new FixtureFootprint(fixture, testItemId));
	}

	protected void reportFeatureStart(Maybe<String> parentId, FeatureInfo featureInfo) {
		StartTestItemRQ rq = createFeatureItemRQ(featureInfo);
		Maybe<String> testItemId = launch.get().startTestItem(parentId, rq);
		registeredFeatureId = testItemId;
		launchContext.addRunningFeature(testItemId, featureInfo);
	}

	public void registerFeature(FeatureInfo feature) {
		if (!feature.isReportIterations()) {
			reportFeatureStart(launchContext.findSpecFootprint(feature.getSpec()).getId(), feature);
		} else if (!feature.isSkipped()) {
			launchContext.addRunningFeature(null, feature);
		}
	}

	public void registerIteration(IterationInfo iteration) {
		if (iteration.getFeature().isReportIterations()) {
			reportIterationStart(launchContext.findSpecFootprint(iteration.getFeature().getSpec()).getId(),
					createIterationItemRQ(iteration),
					iteration
			);
		} else if (iteration.getFeature().isParameterized()) {
			reportIterationStart(launchContext.findFeatureFootprint(iteration.getFeature()).getId(),
					createNestedIterationItemRQ(iteration),
					iteration
			);
		}
	}

	protected FinishTestItemRQ getFinishTestItemRq() {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		return rq;
	}

	protected void reportIterationFinish(ReportableItemFootprint<IterationInfo> footprint) {
		FinishTestItemRQ rq = getFinishTestItemRq();
		rq.setStatus(footprint.getStatus().map(s -> {
			if (SKIPPED == s) {
				rq.setIssue(Launch.NOT_ISSUE);
			}
			return s.name();
		}).orElseGet(() -> {
			footprint.setStatus(ItemStatus.PASSED);
			return PASSED.name();
		}));
		launch.get().finishTestItem(footprint.getId(), rq);
		footprint.markAsPublished();
	}

	protected void reportFeatureFinish(ReportableItemFootprint<FeatureInfo> footprint) {
		FinishTestItemRQ rq = getFinishTestItemRq();
		rq.setStatus(footprint.getStatus().map(s -> {
			if (SKIPPED == s) {
				rq.setIssue(Launch.NOT_ISSUE);
			}
			return s.name();
		}).orElseGet(() -> {
			FeatureInfo feature = footprint.getItem();
			ItemStatus status = ItemStatus.PASSED;
			for (NodeFootprint<IterationInfo> childItemFootprint : launchContext.findIterationFootprints(feature)) {
				for (ReportableItemFootprint<MethodInfo> fixtureFootprint : childItemFootprint.getFixtures()) {
					if (fixtureFootprint.getItem().getKind().isSetupMethod()) {
						status = StatusEvaluation.evaluateStatus(status, fixtureFootprint.getStatus().orElse(null));
					}
				}
				status = StatusEvaluation.evaluateStatus(status, childItemFootprint.getStatus().orElse(null));
			}
			return ofNullable(status).map(Enum::name).orElseGet(() -> {
				LOGGER.error("Unable to calculate status for feature", new IllegalStateException());
				return FAILED.name();
			});
		}));
		launch.get().finishTestItem(footprint.getId(), rq);
		footprint.markAsPublished();
	}

	protected void reportTestItemFinish(ReportableItemFootprint<?> footprint) {
		FinishTestItemRQ rq = getFinishTestItemRq();
		rq.setStatus(footprint.getStatus().map(Enum::name).orElse(ItemStatus.PASSED.name()));
		launch.get().finishTestItem(footprint.getId(), rq);
		footprint.markAsPublished();
	}

	public void publishIterationResult(IterationInfo iteration) {
		FeatureInfo feature = iteration.getFeature();
		if (!feature.isReportIterations() && !feature.isParameterized()) {
			return;
		}
		ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
		reportIterationFinish(footprint);
		registeredFeatureId = null;
	}

	public void publishFeatureResult(FeatureInfo feature) {
		if (feature.isReportIterations()) {
			Iterable<? extends ReportableItemFootprint<IterationInfo>> iterations = launchContext.findIterationFootprints(feature);
			StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterations.iterator(), Spliterator.SIZED), false)
					.filter(IS_NOT_PUBLISHED)
					.forEach(this::reportTestItemFinish);
		} else {
			ReportableItemFootprint<FeatureInfo> footprint = launchContext.findFeatureFootprint(feature);
			reportFeatureFinish(footprint);
		}
	}

	public void publishFixtureResult(SpecInfo spec, FeatureInfo feature, IterationInfo iteration, MethodInfo fixture) {
		NodeFootprint ownerFootprint = findFixtureOwner(feature, iteration, fixture);
		ReportableItemFootprint<MethodInfo> fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(fixture);
		reportTestItemFinish(fixtureFootprint);
	}

	public void publishSpecResult(SpecInfo spec) {
		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		reportTestItemFinish(specFootprint);
	}

	public void reportFixtureError(SpecInfo spec, FeatureInfo feature, IterationInfo iteration, ErrorInfo error) {
		MethodInfo method = error.getMethod();
		NodeFootprint ownerFootprint = findFixtureOwner(feature, iteration, error.getMethod());
		MethodKind kind = method.getKind();
		if (!kind.isCleanupMethod()) {
			NodeFootprint<?> footprint;
			if (feature.isParameterized() || feature.isReportIterations()) {
				footprint = launchContext.findIterationFootprint(iteration);
			} else {
				footprint = launchContext.findFeatureFootprint(feature);
			}
			footprint.setStatus(SKIPPED);
		}
		ReportableItemFootprint<MethodInfo> fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(method);
		fixtureFootprint.setStatus(FAILED);
		Throwable exception = error.getException();
		LoggerFactory.getLogger(error.getMethod().getReflection().getDeclaringClass())
				.error(exception.getLocalizedMessage(), StackTraceUtils.deepSanitize(exception));
	}

	protected void logError(ErrorInfo error) {
		Throwable exception = error.getException();
		LoggerFactory.getLogger(error.getMethod().getReflection().getDeclaringClass()).error(exception.getLocalizedMessage(), exception);
	}

	public void reportError(ErrorInfo error) {
		MethodInfo method = error.getMethod();
		MethodKind kind = error.getMethod().getKind();
		if (FEATURE == kind || FEATURE_EXECUTION == kind) {
			ofNullable(launchContext.findFeatureFootprint(method.getFeature())).ifPresent(f -> f.setStatus(FAILED));
			ofNullable(launchContext.getRuntimePointerForSpec(method.getParent())
					.getCurrentIteration()).map(launchContext::findIterationFootprint).ifPresent(i -> i.setStatus(FAILED));
			logError(error);
		} else if (ITERATION_EXECUTION == kind) {
			ofNullable(launchContext.findIterationFootprint(method.getIteration())).ifPresent(i -> i.setStatus(FAILED));
			logError(error);
		} else if (SPEC_EXECUTION == kind) {
			ofNullable(launchContext.findSpecFootprint(error.getMethod().getFeature().getSpec())).ifPresent(s -> s.setStatus(FAILED));
			logError(error);
		}
	}

	public void trackSkippedFeature(FeatureInfo featureInfo) {
		IterationInfo maskedIteration = buildIterationMaskForFeature(featureInfo);
		reportIterationStart(launchContext.findSpecFootprint(featureInfo.getSpec()).getId(),
				createIterationItemRQ(maskedIteration),
				maskedIteration
		);
		// set skipped status in an appropriate footprint
		ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(maskedIteration);
		footprint.setStatus(SKIPPED);
		// report result of masked iteration
		reportTestItemFinish(footprint);
	}

	public void trackSkippedSpec(SpecInfo spec) {
		registerSpec(spec);
		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		specFootprint.setStatus(SKIPPED);
	}

	public void finishLaunch() {
		if (launchContext.tryFinishLaunch()) {
			// publish all registered unpublished specifications first
			Iterable<? extends NodeFootprint<SpecInfo>> unpublishedSpecFootprints = launchContext.findAllUnpublishedSpecFootprints();
			for (NodeFootprint<SpecInfo> footprint : unpublishedSpecFootprints) {
				reportTestItemFinish(footprint);
			}

			// finish launch
			FinishExecutionRQ rq = createFinishExecutionRQ();
			rq.setEndTime(Calendar.getInstance().getTime());
			launch.get().finish(rq);
			this.launch.reset();
		}
	}

	protected void reportIterationStart(Maybe<String> id, StartTestItemRQ rq, IterationInfo iteration) {
		Maybe<String> testItemId = launch.get().startTestItem(id, rq);
		registeredFeatureId = testItemId;
		launchContext.addRunningIteration(testItemId, iteration);
	}

	void handleRpException(ReportPortalException rpException, String message) {
		handleException(rpException, message);
	}

	/**
	 * Logs error in case of {@link ReportPortalException} or propagates exception exactly as-is, if
	 * and only if it is an instance of {@link RuntimeException} or {@link Error}.
	 */
	private void handleException(Exception exception, String message) {
		if (exception instanceof ReportPortalException) {
			if (LOGGER != null) {
				LOGGER.error(message, exception);
			} else {
				System.out.println(exception.getMessage());
			}
		} else {
			Throwables.throwIfUnchecked(exception);
		}
	}

	NodeFootprint<? extends NodeInfo> findFixtureOwner(FeatureInfo feature, IterationInfo iteration, MethodInfo fixture) {
		MethodKind kind = fixture.getKind();
		if (kind.isSpecScopedFixtureMethod() || (!feature.isParameterized() && !feature.isReportIterations())) {
			return launchContext.findFeatureFootprint(feature);
		} else {
			return launchContext.findIterationFootprint(iteration);
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
		if (!isNullOrEmpty(launchParameters.getDescription())) {
			startLaunchRQ.setDescription(launchParameters.getDescription());
		}
		startLaunchRQ.setStartTime(Calendar.getInstance().getTime());
		if (!launchParameters.getAttributes().isEmpty()) {
			startLaunchRQ.setAttributes(launchParameters.getAttributes());
		}
		startLaunchRQ.setMode(launchParameters.getLaunchRunningMode());
		return startLaunchRQ;
	}

	private StartTestItemRQ createBaseStartTestItemRQ(String name, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		rq.setLaunchUuid(launchContext.getLaunchId().blockingGet());
		return rq;
	}

	private StartTestItemRQ createFeatureItemRQ(FeatureInfo featureInfo) {
		StartTestItemRQ rq = createBaseStartTestItemRQ(featureInfo.getName(), ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setDescription(buildFeatureDescription(featureInfo));
		Description description = featureInfo.getDescription();
		String codeRef = description.getClassName() + "." + description.getMethodName();
		rq.setCodeRef(codeRef);
		Method method = featureInfo.getFeatureMethod().getReflection();
		TestCaseId testCaseId = method.getAnnotation(TestCaseId.class);
		rq.setTestCaseId(ofNullable(TestCaseIdUtils.getTestCaseId(testCaseId, method, codeRef, null)).map(TestCaseIdEntry::getId)
				.orElse(null));
		return rq;
	}

	private StartTestItemRQ createIterationItemRQ(IterationInfo iteration) {
		StartTestItemRQ rq = createBaseStartTestItemRQ(iteration.getName(), ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setDescription(buildIterationDescription(iteration));
		FeatureInfo featureInfo = iteration.getFeature();
		Description description = featureInfo.getDescription();
		String codeRef = description.getClassName() + "." + description.getMethodName();
		rq.setCodeRef(codeRef);
		Method method = featureInfo.getFeatureMethod().getReflection();
		TestCaseId testCaseId = method.getAnnotation(TestCaseId.class);
		List<Object> params = ofNullable(iteration.getDataValues()).map(Arrays::asList).orElse(null);
		rq.setTestCaseId(ofNullable(TestCaseIdUtils.getTestCaseId(testCaseId, method, codeRef, params)).map(TestCaseIdEntry::getId)
				.orElse(null));
		List<Object> paramList = ofNullable(params).orElse(Collections.emptyList());
		List<String> names = iteration.getFeature().getParameterNames();
		rq.setParameters(ParameterUtils.getParameters(codeRef,
				IntStream.range(0, paramList.size()).mapToObj(i -> Pair.of(names.get(i), paramList.get(i))).collect(Collectors.toList())
		));
		return rq;
	}

	private StartTestItemRQ createNestedIterationItemRQ(IterationInfo iteration) {
		List<Object> params = Arrays.asList(iteration.getDataValues());
		List<String> names = iteration.getFeature().getParameterNames();
		String name = IntStream.range(0, params.size()).mapToObj(i -> {
			Object p = params.get(i);
			String n;
			try {
				n = names.get(i);
				n = ofNullable(n).orElse("param" + i + 1);
			} catch (IndexOutOfBoundsException e) {
				n = "param" + i + 1;
			}
			return Pair.of(n, p);
		}).map(p -> p.getKey() + ": " + p.getValue()).collect(Collectors.joining("; ", "Parameters: ", ""));
		StartTestItemRQ rq = createBaseStartTestItemRQ(name, ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setHasStats(false);

		return rq;
	}

	@Override
	public void beforeSpec(SpecInfo spec) {
		registerSpec(spec);
		for (MethodInfo fixture : spec.getAllFixtureMethods()) {
			fixture.addInterceptor(new FixtureInterceptor(this));
		}
	}

	@Override
	public void beforeFeature(FeatureInfo feature) {
		registerFeature(feature);
	}

	@Override
	public void beforeIteration(IterationInfo iteration) {
		registerIteration(iteration);
	}

	@Override
	public void afterIteration(IterationInfo iteration) {
		publishIterationResult(iteration);
	}

	@Override
	public void afterFeature(FeatureInfo feature) {
		publishFeatureResult(feature);
	}

	@Override
	public void afterSpec(SpecInfo spec) {
		publishSpecResult(spec);
	}

	@Override
	public void error(ErrorInfo error) {
		reportError(error);
	}

	@Override
	public void featureSkipped(FeatureInfo feature) {
		registerFeature(feature);
		trackSkippedFeature(feature);
	}

	@Override
	public void specSkipped(SpecInfo spec) {
		trackSkippedSpec(spec);
		publishSpecResult(spec);
	}
}
