package com.epam.reportportal.spock;

import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.model.ErrorInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * @author Dzmitry Mikhievich
 */
class ReportableRunListener extends AbstractRunListener {

	private final ISpockReporter spockReporter;

	public ReportableRunListener(ISpockReporter spockReporter) {
		this.spockReporter = spockReporter;
	}

	@Override
	public void beforeFeature(FeatureInfo feature) {
		spockReporter.registerFeature(feature);
	}

	@Override
	public void beforeIteration(IterationInfo iteration) {
		spockReporter.registerIteration(iteration);
	}

	@Override
	public void afterIteration(IterationInfo iteration) {
		spockReporter.publishIterationResult(iteration);
	}

	@Override
	public void afterFeature(FeatureInfo feature) {
		spockReporter.publishFeatureResult(feature);
	}

	@Override
	public void afterSpec(SpecInfo spec) {
		spockReporter.publishSpecResult(spec);
	}

	@Override
	public void error(ErrorInfo error) {
		spockReporter.reportError(error);
	}

	@Override
	public void featureSkipped(FeatureInfo feature) {
		spockReporter.registerFeature(feature);
		spockReporter.trackSkippedFeature(feature);
	}

	@Override
	public void specSkipped(SpecInfo spec) {
		spockReporter.registerSpec(spec);
		spockReporter.trackSkippedSpec(spec);
		spockReporter.publishSpecResult(spec);
	}
}