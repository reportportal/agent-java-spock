package com.epam.reportportal.spock;

import org.spockframework.runtime.model.ErrorInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * @author Dzmitry Mikhievich
 */
interface ISpockReporter {

	void startLaunch();

	void registerSpec(SpecInfo spec);

	void registerFeature(FeatureInfo feature);

	void registerIteration(IterationInfo iteration);

	void trackSkippedFeature(FeatureInfo featureInfo);

	void trackSkippedSpec(SpecInfo spec);

	void publishIterationResult(IterationInfo iteration);

	void publishFeatureResult(FeatureInfo feature);

	void publishSpecResult(SpecInfo spec);

	void reportError(ErrorInfo errorInfo);

	void finishLaunch();
}
