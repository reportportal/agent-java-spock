package com.epam.reportportal.spock;

import com.epam.reportportal.exception.ReportPortalException;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.restendpoint.http.exception.RestEndpointIOException;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.LoggingContext;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.issue.Issue;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.*;
import org.spockframework.util.ExceptionUtil;
import rp.com.google.common.base.Throwables;
import rp.com.google.common.annotations.VisibleForTesting;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Optional;
import rp.com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.epam.reportportal.listeners.Statuses.FAILED;
import static com.epam.reportportal.listeners.Statuses.SKIPPED;
import static com.epam.reportportal.spock.NodeInfoUtils.buildIterationDescription;
import static com.epam.reportportal.spock.NodeInfoUtils.getFixtureDisplayName;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
import static org.spockframework.runtime.model.MethodKind.*;
import static rp.com.google.common.base.Optional.fromNullable;
import static rp.com.google.common.base.Preconditions.checkArgument;
import static rp.com.google.common.base.Strings.isNullOrEmpty;
import static rp.com.google.common.collect.Iterables.getFirst;
import static rp.com.google.common.collect.Iterables.filter;


/**
 * Spock service implements operations for interaction with report portal
 */
public class SpockService implements ISpockService
{
    public static final String NOT_ISSUE = "NOT_ISSUE";
    private final AtomicBoolean isLaunchFailed = new AtomicBoolean();
    private MemoizingSupplier<Launch> launch;
    private static final Logger LOGGER = LoggerFactory.getLogger(SpockService.class);

    // stores the bindings of Spock method kinds to the RP-specific notation
    private static final Map<MethodKind, String> ITEM_TYPES_REGISTRY = ImmutableMap.<MethodKind, String> builder()
            .put(SPEC_EXECUTION, "SUITE").put(SETUP_SPEC, "BEFORE_CLASS").put(SETUP, "BEFORE_METHOD").put(FEATURE, "TEST")
            .put(CLEANUP, "AFTER_METHOD").put(CLEANUP_SPEC, "AFTER_CLASS").build();

    private final AtomicBoolean rpIsDown = new AtomicBoolean(false);
    private ListenerParameters launchParameters;
    private final AbstractLaunchContext launchContext;
    private ReportPortalClient rpClient;
    private Maybe<String> registeredFeatureId = null;


    public SpockService() {

        launchContext = new LaunchContextImpl();
        this.launch = new MemoizingSupplier<Launch>(new Supplier<Launch>() {
            @Override
            public Launch get() {
                //this reads property, so we want to
                //init ReportPortal object each time Launch object is going to be created
                final ReportPortal reportPortal = ReportPortal.builder().build();

                launchParameters = reportPortal.getParameters();
                StartLaunchRQ rq = createStartLaunchRQ();
                rpClient = reportPortal.getClient();

                rq.setStartTime(Calendar.getInstance().getTime());
                return reportPortal.newLaunch(rq);
            }
        });
    }

    public SpockService(Supplier<Launch> launch)
    {
        checkArgument(launch != null, "launch shouldn't be null");
        launchContext = new LaunchContextImpl();
        this.launch = new MemoizingSupplier<Launch>(launch);
    }

    public SpockService(Supplier<Launch> launch, AbstractLaunchContext lnchContext)
    {
        checkArgument(launch != null, "launch shouldn't be null");
        launchContext = lnchContext;
        this.launch = new MemoizingSupplier<Launch>(launch);
    }

    @Override
    public void startLaunch() {
        if (launchContext.tryStartLaunch())
        {
            try
            {
                Maybe<String> launchId = this.launch.get().start();
                launchContext.setLaunchId(launchId);
            }
            catch (RestEndpointIOException ex)
            {
                handleRpException(ex, "Unable start the launch: '" + launchParameters.getLaunchName() + "'");
            }
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
            Maybe<String> testItemId = this.launch.get().startTestItem(rq);
            launchContext.addRunningSpec(testItemId, spec);
        } catch (RestEndpointIOException ex) {
            handleRpException(ex, "Unable start spec: '" + spec.getName() + "'");
        }
    }

    @Override
    public void registerFixture(MethodInfo fixture)
    {
        if (rpIsDown.get()) {
            return;
        }

        NodeFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(fixture.getParent());
        boolean isFixtureInherited = !fixture.getParent().equals(specFootprint.getItem());
        String fixtureDisplayName = getFixtureDisplayName(fixture, isFixtureInherited);
        MethodKind kind = fixture.getKind();
        StartTestItemRQ rq = createBaseStartTestItemRQ(fixtureDisplayName, ITEM_TYPES_REGISTRY.get(kind));
        try {
            if(registeredFeatureId != null && kind == CLEANUP)
            {
                LoggingContext.complete();
            }

            Maybe<String> testItemId = this.launch.get().startTestItem(specFootprint.getId(), rq);
            NodeFootprint fixtureOwnerFootprint = findFixtureOwner(fixture);
            fixtureOwnerFootprint.addFixtureFootprint(new FixtureFootprint(fixture, testItemId));
        } catch (RestEndpointIOException ex) {
            handleRpException(ex, "Unable to start '" + fixtureDisplayName + "' fixture");
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
    public void publishIterationResult(IterationInfo iteration) {
        if (rpIsDown.get()) {
            return;
        }

        FeatureInfo feature = iteration.getFeature();
        if (!isMonolithicParametrizedFeature(feature)) {
            ReportableItemFootprint<IterationInfo> footprint = launchContext.findIterationFootprint(iteration);
            reportTestItemFinish(footprint);
            registeredFeatureId = null;
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
    public void publishFixtureResult(MethodInfo fixture) {
        if (rpIsDown.get()) {
            return;
        }

        NodeFootprint ownerFootprint = findFixtureOwner(fixture);
        ReportableItemFootprint fixtureFootprint = ownerFootprint.findUnpublishedFixtureFootprint(fixture);
        reportTestItemFinish(fixtureFootprint);
        MethodKind kind = fixture.getKind();
        if(registeredFeatureId != null && kind == SETUP)
        {
            LoggingContext.init(registeredFeatureId, rpClient);
        }
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

            // Explicitly register specification here, because in the case of
            // shared initializer error appropriate listener method isn't triggered

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
            reportTestItemFailure(error);
        }
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
    public void finishLaunch() {
        if (launchContext.tryFinishLaunch()) {
            // publish all registered unpublished specifications first
            Iterable<? extends NodeFootprint<SpecInfo>> unpublishedSpecFootprints = launchContext.findAllUnpublishedSpecFootprints();
            for (NodeFootprint<SpecInfo> footprint : unpublishedSpecFootprints) {
                reportTestItemFinish(footprint);
            }

            // finish launch
            FinishExecutionRQ rq = createFinishExecutionRQ();
            rq.setStatus(isLaunchFailed.get() ? Statuses.FAILED : Statuses.PASSED);

            try {
                launch.get().finish(rq);
            } catch (RestEndpointIOException ex) {
                handleRpException(ex, "Unable finish the launch: '" + launchContext.getLaunchId() + "'");
            }
            this.launch.reset();
        }
    }

    @VisibleForTesting
    void reportIterationStart(IterationInfo iteration) {
        StartTestItemRQ rq = createBaseStartTestItemRQ(iteration.getName(), ITEM_TYPES_REGISTRY.get(FEATURE));
        rq.setDescription(buildIterationDescription(iteration));
        ReportableItemFootprint<SpecInfo> specFootprint = launchContext.findSpecFootprint(iteration.getFeature().getSpec());
        try {
            Maybe<String> testItemId = launch.get().startTestItem(specFootprint.getId(), rq);
            registeredFeatureId = testItemId;
            launchContext.addRunningIteration(testItemId, iteration);
        } catch (RestEndpointIOException ex) {
            handleRpException(ex, "Unable start test method: '" + iteration.getName() + "'");
        }
    }

    @VisibleForTesting
    void reportTestItemFinish(ReportableItemFootprint<?> footprint) {
        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());

        // Determine whether skipped test is to be investigated or not
        boolean failLaunch = false;

        // Check if test item has SKIPPED status
        String status = "EMPTY";
        if(footprint.getStatus().isPresent())
        {
            status = footprint.getStatus().get();
        }

        if(SKIPPED.equalsIgnoreCase(status))
        {
            failLaunch = true;

            // If status is SKIPPED determine whether to investigate it or not
            if(!fromNullable(launch.get().getParameters().getSkippedAnIssue()).or(false))
            {
                Issue issue = new Issue();
                issue.setIssueType(NOT_ISSUE);
                rq.setIssue(issue);
            }
        }

        // Check if fixture items failed for an iteration - if so, then fail the iteration
        if(footprint.getItem() instanceof IterationInfo)
        {
            List<ReportableItemFootprint<MethodInfo>> fixtures = ((NodeFootprint<IterationInfo>)footprint).getFixtures();

            boolean fixtureError = false;
            for(ReportableItemFootprint<MethodInfo> methodInfo : fixtures)
            {
                if(methodInfo.getItem().getKind() != CLEANUP)
                {
                    Optional<String> methodStatus = methodInfo.getStatus();

                    if (methodStatus.isPresent())
                    {
                        if (methodStatus.get().equalsIgnoreCase(Statuses.FAILED))
                        {
                            fixtureError = true;
                            break;
                        }
                    }
                }
            }

            if(fixtureError)
            {
                footprint.setStatus(Statuses.FAILED);
                Issue issue = new Issue();
                issue.setIssueType(NOT_ISSUE);
                rq.setIssue(issue);
            }
        }

        String footprintStatus = calculateFootprintStatus(footprint);
        rq.setStatus(footprintStatus);

        if(!failLaunch)
        {
            isLaunchFailed.compareAndSet(false, FAILED.equalsIgnoreCase(footprintStatus));
        }
        else
        {
            isLaunchFailed.set(true);
        }

        try {
            launch.get().finishTestItem(footprint.getId(), rq);
        } catch (RestEndpointIOException ex) {
            handleRpException(ex, "Unable finish " + footprint.getClass().getSimpleName() + ": '" + footprint.getItemName() + "'");
        } finally {
            footprint.markAsPublished();
        }
    }

    @VisibleForTesting
    void reportTestItemFailure(final ErrorInfo errorInfo)
    {
        String message = "Exception: " + ExceptionUtil.printStackTrace(errorInfo.getException());
        String level = "ERROR";
        Date time = Calendar.getInstance().getTime();
        ReportPortal.emitLog(message, level, time);
    }

    @VisibleForTesting
    void handleRpException(RestEndpointIOException rpException, String message) {
        rpIsDown.set(true);
        handleException(rpException, message);
    }

    /**
     * Logs error in case of {@link ReportPortalException} or
     * {@link RestEndpointIOException} or propagates exception exactly as-is, if
     * and only if it is an instance of {@link RuntimeException} or {@link Error}.
     *
     * @param exception
     * @param message
     */
    private void handleException(Exception exception, String message) {
        if (exception instanceof ReportPortalException || RestEndpointIOException.class.equals(exception.getClass())) {
            if (LOGGER != null) {
                LOGGER.error(message, exception);
            } else {
                System.out.println(exception.getMessage());
            }
        } else {
            Throwables.throwIfUnchecked(exception);
        }
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
        IterationInfo iterationInfo = new IterationInfo(featureInfo,  new String[]{"NOT PROVIDED"}, 0);
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
        startLaunchRQ.setTags(launchParameters.getTags());
        startLaunchRQ.setMode(launchParameters.getLaunchRunningMode());
        return startLaunchRQ;
    }

    private StartTestItemRQ createBaseStartTestItemRQ(String name, String type) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(name);
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType(type);
        rq.setLaunchId(launchContext.getLaunchId().blockingGet());
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

    @VisibleForTesting
    static class MemoizingSupplier<T> implements Supplier<T>, Serializable
    {
        final Supplier<T> delegate;
        transient volatile boolean initialized;
        transient T value;
        private static final long serialVersionUID = 0L;

        MemoizingSupplier(Supplier<T> delegate) {
            this.delegate = delegate;
        }

        public T get() {
            if (!this.initialized) {
                synchronized (this) {
                    if (!this.initialized) {
                        T t = this.delegate.get();
                        this.value = t;
                        this.initialized = true;
                        return t;
                    }
                }
            }

            return this.value;
        }

        public synchronized void reset() {
            this.initialized = false;
        }

        public String toString() {
            return "Suppliers.memoize(" + this.delegate + ")";
        }
    }
}
