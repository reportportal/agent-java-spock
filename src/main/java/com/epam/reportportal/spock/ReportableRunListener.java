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

import static com.google.common.base.Preconditions.checkArgument;

import javax.inject.Inject;

import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.model.*;

/**
 * Implementation of {@link org.spockframework.runtime.AbstractRunListener}, which transmits <i>Spock</i> runtime events
 * to the {@link ISpockReporter} instance and adds provided {@link IMethodInterceptor} fixtures hook for each passed <i>spec</i>
 *
 * @author Dzmitry Mikhievich
 */
class ReportableRunListener extends AbstractRunListener {

	private final ISpockReporter spockReporter;
	private final IMethodInterceptor fixturesInterceptor;

	@Inject
	public ReportableRunListener(ISpockReporter spockReporter, IMethodInterceptor fixturesInterceptor) {
		checkArgument(spockReporter != null, "Spock reporter shouldn't be null");
		checkArgument(fixturesInterceptor != null, "Fixture interceptor shouldn't be null");

		this.spockReporter = spockReporter;
		this.fixturesInterceptor = fixturesInterceptor;
	}

	@Override
	public void beforeSpec(SpecInfo spec) {
		spockReporter.registerSpec(spec);
		for (MethodInfo fixture : spec.getAllFixtureMethods()) {
			fixture.addInterceptor(fixturesInterceptor);
		}
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
		spockReporter.trackSkippedSpec(spec);
		spockReporter.publishSpecResult(spec);
	}
}