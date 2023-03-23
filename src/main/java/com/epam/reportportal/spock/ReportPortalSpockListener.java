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
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.exception.ReportPortalException;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.spock.utils.SystemAttributesFetcher;
import com.epam.reportportal.utils.*;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.model.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static com.epam.reportportal.listeners.ItemStatus.*;
import static com.epam.reportportal.spock.NodeInfoUtils.*;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
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

	@Nonnull
	protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
		StartLaunchRQ startLaunchRQ = new StartLaunchRQ();
		startLaunchRQ.setName(parameters.getLaunchName());
		if (!isNullOrEmpty(parameters.getDescription())) {
			startLaunchRQ.setDescription(parameters.getDescription());
		}
		startLaunchRQ.setStartTime(Calendar.getInstance().getTime());
		Set<ItemAttributesRQ> attributes = new HashSet<>();
		attributes.addAll(parameters.getAttributes());
		attributes.addAll(SystemAttributesFetcher.collectSystemAttributes(parameters.getSkippedAnIssue()));
		startLaunchRQ.setAttributes(attributes);
		startLaunchRQ.setMode(parameters.getLaunchRunningMode());

		return startLaunchRQ;
	}

	public ReportPortalSpockListener(final ReportPortal reportPortal) {
		launchContext = new LaunchContextImpl();
		launchParameters = reportPortal.getParameters();
		this.launch = new MemoizingSupplier<>(() -> {
			StartLaunchRQ rq = buildStartLaunchRq(launchParameters);
			return reportPortal.newLaunch(rq);
		});
	}

	public ReportPortalSpockListener() {
		this(ReportPortal.builder().build());
	}

	public ReportPortalSpockListener(@Nonnull Supplier<Launch> launch, AbstractLaunchContext launchContext) {
		this.launchContext = launchContext;
		this.launch = new MemoizingSupplier<>(launch);
	}

	public ReportPortalSpockListener(@Nonnull Supplier<Launch> launch) {
		this(launch, new LaunchContextImpl());
	}

	public Maybe<String> startLaunch() {
		if (launchContext.tryStartLaunch()) {
			try {
				Maybe<String> launchId = this.launch.get().start();
				launchContext.setLaunchId(launchId);
				return launchId;
			} catch (ReportPortalException ex) {
				handleRpException(ex,
						"Unable start the launch: '" + ofNullable(launchParameters).map(ListenerParameters::getLaunchName)
								.orElse("Unknown Launch") + "'"
				);
			}
		}
		return launchContext.getLaunchId();
	}

	protected void setAttributes(@Nonnull StartTestItemRQ rq, @Nonnull AnnotatedElement methodOrClass) {
		Attributes attributes = methodOrClass.getAnnotation(Attributes.class);
		if (attributes != null) {
			Set<ItemAttributesRQ> itemAttributes = AttributeParser.retrieveAttributes(attributes);
			rq.setAttributes(itemAttributes);
		}
	}

	@Nonnull
	protected StartTestItemRQ buildBaseStartTestItemRq(@Nonnull String name, @Nonnull String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		rq.setLaunchUuid(launchContext.getLaunchId().blockingGet());
		return rq;
	}

	protected void setSpecAttributes(@Nonnull StartTestItemRQ rq, @Nonnull SpecInfo spec) {
		setAttributes(rq, spec.getReflection());
	}

	@Nonnull
	protected StartTestItemRQ buildSpecItemRq(@Nonnull SpecInfo spec) {
		StartTestItemRQ rq = buildBaseStartTestItemRq(spec.getName(), ITEM_TYPES_REGISTRY.get(SPEC_EXECUTION));
		rq.setDescription(spec.getNarrative());
		rq.setCodeRef(spec.getReflection().getCanonicalName());
		setSpecAttributes(rq, spec);
		return rq;
	}

	@Nonnull
	protected Maybe<String> startSpec(@Nonnull StartTestItemRQ rq) {
		return launch.get().startTestItem(rq);
	}

	public void registerSpec(@Nonnull SpecInfo spec) {
		if (launchContext.isSpecRegistered(spec)) {
			return;
		}
		Maybe<String> testItemId = startSpec(buildSpecItemRq(spec));
		launchContext.addRunningSpec(testItemId, spec);
	}

	@Nonnull
	protected StartTestItemRQ buildFixtureItemRq(@Nonnull FeatureInfo feature, @Nonnull MethodInfo fixture, boolean inherited) {
		MethodKind kind = fixture.getKind();
		String fixtureDisplayName = getFixtureDisplayName(fixture, inherited);
		StartTestItemRQ rq = buildBaseStartTestItemRq(fixtureDisplayName, ITEM_TYPES_REGISTRY.get(kind));
		if (kind.isFeatureScopedFixtureMethod() && !feature.isReportIterations() && feature.isParameterized()) {
			rq.setHasStats(false);
		}
		return rq;
	}

	@Nonnull
	protected Maybe<String> startFixture(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq) {
		return launch.get().startTestItem(parentId, rq);
	}

	public void registerFixture(SpecInfo spec, @Nonnull FeatureInfo feature, IterationInfo iteration, @Nonnull MethodInfo fixture) {
		NodeFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		StartTestItemRQ rq = buildFixtureItemRq(feature, fixture, !fixture.getParent().equals(specFootprint.getItem()));
		Maybe<String> testItemId = startFixture(rq.isHasStats() ?
				specFootprint.getId() :
				launchContext.findFeatureFootprint(feature).getId(), rq);
		@SuppressWarnings("rawtypes")
		NodeFootprint<? extends NodeInfo> fixtureOwnerFootprint = findFixtureOwner(spec, feature, iteration, fixture);
		fixtureOwnerFootprint.addFixtureFootprint(new FixtureFootprint(fixture, testItemId));
	}

	@Nonnull
	protected StartTestItemRQ buildNestedIterationItemRq(@Nonnull IterationInfo iteration) {
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
		StartTestItemRQ rq = buildBaseStartTestItemRq(name, ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setHasStats(false);

		return rq;
	}

	@Nonnull
	protected StartTestItemRQ buildIterationItemRq(@Nonnull IterationInfo iteration) {
		StartTestItemRQ rq = buildBaseStartTestItemRq(iteration.getDisplayName(), ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setDescription(buildIterationDescription(iteration));
		MethodInfo featureMethodInfo = iteration.getFeature().getFeatureMethod();
		String codeRef = extractCodeRef(featureMethodInfo);
		rq.setCodeRef(codeRef);
		Method method = featureMethodInfo.getReflection();
		TestCaseId testCaseId = method.getAnnotation(TestCaseId.class);
		List<Object> params = ofNullable(iteration.getDataValues()).map(Arrays::asList).orElse(null);
		rq.setTestCaseId(ofNullable(TestCaseIdUtils.getTestCaseId(testCaseId, method, codeRef, params)).map(TestCaseIdEntry::getId)
				.orElse(null));
		List<Object> paramList = ofNullable(params).orElse(Collections.emptyList());
		List<String> names = iteration.getFeature().getParameterNames();
		rq.setParameters(ParameterUtils.getParameters(codeRef,
				IntStream.range(0, paramList.size()).mapToObj(i -> Pair.of(names.get(i), paramList.get(i))).collect(Collectors.toList())
		));
		setFeatureAttributes(rq, iteration.getFeature());
		return rq;
	}

	@Nonnull
	protected Maybe<String> startIteration(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq) {
		return launch.get().startTestItem(parentId, rq);
	}

	protected void reportIterationStart(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq, @Nonnull IterationInfo iteration) {
		Maybe<String> testItemId = startIteration(parentId, rq);
		launchContext.addRunningIteration(testItemId, iteration);
	}

	public void registerIteration(@Nonnull IterationInfo iteration) {
		if (iteration.getFeature().isReportIterations()) {
			reportIterationStart(launchContext.findSpecFootprint(iteration.getFeature().getSpec()).getId(),
					buildIterationItemRq(iteration),
					iteration
			);
		} else if (iteration.getFeature().isParameterized()) {
			reportIterationStart(launchContext.findFeatureFootprint(iteration.getFeature()).getId(),
					buildNestedIterationItemRq(iteration),
					iteration
			);
		}
	}

	/**
	 * Build finish test item request object
	 *
	 * @param itemId item ID reference
	 * @param status item result status
	 * @return finish request
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishTestItemRq(@Nonnull Maybe<String> itemId, @Nullable ItemStatus status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		ofNullable(status).ifPresent(s -> rq.setStatus(s.name()));
		rq.setEndTime(Calendar.getInstance().getTime());
		return rq;
	}

	protected void reportIterationFinish(@Nonnull ReportableItemFootprint<IterationInfo> footprint) {
		ItemStatus status = footprint.getStatus().orElseGet(() -> {
			footprint.setStatus(ItemStatus.PASSED);
			return PASSED;
		});
		FinishTestItemRQ rq = buildFinishTestItemRq(footprint.getId(), status);
		if (SKIPPED == status) {
			rq.setIssue(Launch.NOT_ISSUE);
		}
		launch.get().finishTestItem(footprint.getId(), rq);
		footprint.markAsPublished();
	}

	protected void reportFeatureFinish(@Nonnull ReportableItemFootprint<FeatureInfo> footprint) {
		ItemStatus status = footprint.getStatus().orElseGet(() -> {
			FeatureInfo feature = footprint.getItem();
			ItemStatus s = ItemStatus.PASSED;
			for (NodeFootprint<IterationInfo> childItemFootprint : launchContext.findIterationFootprints(feature)) {
				for (ReportableItemFootprint<MethodInfo> fixtureFootprint : childItemFootprint.getFixtures()) {
					if (fixtureFootprint.getItem().getKind().isSetupMethod()) {
						s = StatusEvaluation.evaluateStatus(s, fixtureFootprint.getStatus().orElse(null));
					}
				}
				s = StatusEvaluation.evaluateStatus(s, childItemFootprint.getStatus().orElse(null));
			}
			return ofNullable(s).orElseGet(() -> {
				LOGGER.error("Unable to calculate status for feature", new IllegalStateException());
				return FAILED;
			});
		});

		Maybe<String> itemId = footprint.getId();
		FinishTestItemRQ rq = buildFinishTestItemRq(itemId, status);
		if (SKIPPED == status) {
			rq.setIssue(Launch.NOT_ISSUE);
		}
		launch.get().finishTestItem(itemId, rq);
		footprint.markAsPublished();
	}

	protected void reportTestItemFinish(@Nonnull ReportableItemFootprint<?> footprint) {
		Maybe<String> itemId = footprint.getId();
		FinishTestItemRQ rq = buildFinishTestItemRq(itemId, footprint.getStatus().orElse(ItemStatus.PASSED));
		launch.get().finishTestItem(itemId, rq);
		footprint.markAsPublished();
	}

	public void publishFeatureResult(@Nonnull FeatureInfo feature) {
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

	@Nonnull
	protected StartTestItemRQ buildFeatureItemRq(@Nonnull FeatureInfo featureInfo) {
		StartTestItemRQ rq = buildBaseStartTestItemRq(featureInfo.getName(), ITEM_TYPES_REGISTRY.get(FEATURE));
		rq.setDescription(buildFeatureDescription(featureInfo));
		MethodInfo featureMethodInfo = featureInfo.getFeatureMethod();
		String codeRef = extractCodeRef(featureMethodInfo);
		rq.setCodeRef(codeRef);
		Method method = featureMethodInfo.getReflection();
		TestCaseId testCaseId = method.getAnnotation(TestCaseId.class);
		rq.setTestCaseId(ofNullable(TestCaseIdUtils.getTestCaseId(testCaseId, method, codeRef, null)).map(TestCaseIdEntry::getId)
				.orElse(null));
		setFeatureAttributes(rq, featureInfo);
		return rq;
	}

	@Nonnull
	protected Maybe<String> startFeature(@Nonnull Maybe<String> parentId, @Nonnull StartTestItemRQ rq) {
		return launch.get().startTestItem(parentId, rq);
	}

	protected void reportFeatureStart(@Nonnull Maybe<String> parentId, @Nonnull FeatureInfo featureInfo) {
		StartTestItemRQ rq = buildFeatureItemRq(featureInfo);
		launchContext.addRunningFeature(startFeature(parentId, rq), featureInfo);
	}

	public void reportFixtureError(@Nonnull SpecInfo spec, @Nullable FeatureInfo feature, @Nullable IterationInfo iteration,
			@Nonnull ErrorInfo error) {
		MethodInfo method = error.getMethod();
		NodeFootprint<?> ownerFootprint = findFixtureOwner(spec, feature, iteration, error.getMethod());
		MethodKind kind = method.getKind();
		NodeFootprint<?> specFootprint = launchContext.findSpecFootprint(spec);
		specFootprint.setStatus(FAILED);
		if (!kind.isCleanupMethod()) {
			if (feature != null) {
				// Failed before / after feature
				NodeFootprint<?> footprint;
				if (feature.isParameterized() || feature.isReportIterations()) {
					footprint = launchContext.findIterationFootprint(iteration);
				} else {
					footprint = launchContext.findFeatureFootprint(feature);
				}
				footprint.setStatus(SKIPPED);
			} else {
				// Failed before spec
				spec.getFeatures().forEach(f -> {
					reportFeatureStart(specFootprint.getId(), f);
					NodeFootprint<FeatureInfo> ff = launchContext.findFeatureFootprint(f);
					ff.setStatus(SKIPPED);
					reportFeatureFinish(ff);
				});
			}
		}
		ReportableItemFootprint<MethodInfo> fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(method);
		fixtureFootprint.setStatus(FAILED);
		Throwable exception = error.getException();
		LoggerFactory.getLogger(error.getMethod().getReflection().getDeclaringClass())
				.error(exception.getLocalizedMessage(), StackTraceUtils.deepSanitize(exception));
	}

	protected void logError(@Nonnull ErrorInfo error) {
		Throwable exception = error.getException();
		LoggerFactory.getLogger(error.getMethod().getReflection().getDeclaringClass()).error(exception.getLocalizedMessage(), exception);
	}

	public void reportError(@Nonnull ErrorInfo error) {
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

	protected void trackSkippedSpec(SpecInfo spec) {
		registerSpec(spec);
		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		specFootprint.setStatus(SKIPPED);
	}

	protected void trackSkippedFeature(FeatureInfo feature) {
		reportFeatureStart(launchContext.findSpecFootprint(feature.getSpec()).getId(), feature);
		NodeFootprint<FeatureInfo> footprint = launchContext.findFeatureFootprint(feature);
		footprint.setStatus(SKIPPED);
	}

	@Nonnull
	private FinishExecutionRQ buildFinishExecutionRq() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		return rq;
	}

	public void finishLaunch() {
		if (launchContext.tryFinishLaunch()) {
			// publish all registered unpublished specifications first
			Iterable<? extends NodeFootprint<SpecInfo>> unpublishedSpecFootprints = launchContext.findAllUnpublishedSpecFootprints();
			for (NodeFootprint<SpecInfo> footprint : unpublishedSpecFootprints) {
				reportTestItemFinish(footprint);
			}

			// finish launch
			FinishExecutionRQ rq = buildFinishExecutionRq();
			launch.get().finish(rq);
			this.launch.reset();
		}
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

	@SuppressWarnings("rawtypes")
	protected NodeFootprint<? extends NodeInfo> findFixtureOwner(SpecInfo spec, FeatureInfo feature, IterationInfo iteration,
			MethodInfo fixture) {
		MethodKind kind = fixture.getKind();
		if (kind.isSpecScopedFixtureMethod()) {
			return launchContext.findSpecFootprint(spec);
		} else if (!feature.isParameterized() && !feature.isReportIterations()) {
			return launchContext.findFeatureFootprint(feature);
		} else {
			return launchContext.findIterationFootprint(iteration);
		}
	}

	protected void setFeatureAttributes(@Nonnull StartTestItemRQ rq, @Nonnull FeatureInfo featureInfo) {
		setAttributes(rq, featureInfo.getFeatureMethod().getReflection());
	}

	public void registerFeature(@Nonnull FeatureInfo feature) {
		if (!feature.isReportIterations()) {
			reportFeatureStart(launchContext.findSpecFootprint(feature.getSpec()).getId(), feature);
		} else if (!feature.isSkipped()) {
			launchContext.addRunningFeature(null, feature);
		}
	}

	public void publishSpecResult(@Nonnull SpecInfo spec) {
		ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(spec);
		reportTestItemFinish(specFootprint);
	}

	public void publishIterationResult(@Nonnull IterationInfo iteration) {
		FeatureInfo feature = iteration.getFeature();
		if (!feature.isReportIterations() && !feature.isParameterized()) {
			return;
		}
		ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
		reportIterationFinish(footprint);
	}

	@SuppressWarnings("rawtypes")
	public void publishFixtureResult(SpecInfo spec, FeatureInfo feature, IterationInfo iteration, MethodInfo fixture) {
		NodeFootprint<? extends NodeInfo> ownerFootprint = findFixtureOwner(spec, feature, iteration, fixture);
		ReportableItemFootprint<MethodInfo> fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(fixture);
		reportTestItemFinish(fixtureFootprint);
	}

	@Override
	public void beforeSpec(@Nonnull SpecInfo spec) {
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
		trackSkippedFeature(feature);
		reportTestItemFinish(launchContext.findFeatureFootprint(feature));
	}

	@Override
	public void specSkipped(SpecInfo spec) {
		trackSkippedSpec(spec);
		publishSpecResult(spec);
	}

	private String extractCodeRef(MethodInfo featureMethodInfo) {
		String iterationClassName = featureMethodInfo.getReflection().getDeclaringClass().getCanonicalName();
		String iterationMethodName = featureMethodInfo.getName();
		return iterationClassName + "." + iterationMethodName;
	}
}
