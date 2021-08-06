package com.epam.reportportal.spock;

import org.spockframework.runtime.model.*;


/**
 * Describes all operations for com.epam.reportportal.spock RP listener handler
 */
public interface ISpockService {
    /**
     * Trigger test execution start
     */
    void startLaunch();

    void registerFixture(MethodInfo fixture);

    void registerSpec(SpecInfo spec);

    void registerFeature(FeatureInfo feature);

    void registerIteration(IterationInfo iteration);

    /**
     * Handle feature skipping
     *
     * @param featureInfo skipped feature
     */
    void trackSkippedFeature(FeatureInfo featureInfo);

    /**
     * Handle specification skipping
     *
     * @param specInfo skipped feature
     */
    void trackSkippedSpec(SpecInfo specInfo);

    void publishFixtureResult(MethodInfo fixture);

    void publishIterationResult(IterationInfo iteration);

    void publishFeatureResult(FeatureInfo feature);

    void publishSpecResult(SpecInfo spec);

    void reportError(ErrorInfo errorInfo);

    /**
     * Trigger test execution end
     */
    void finishLaunch();
}
