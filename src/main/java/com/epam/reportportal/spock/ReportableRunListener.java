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

import static com.google.common.base.Preconditions.checkArgument;

import javax.inject.Inject;

import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.model.*;

/**
 * @author Dzmitry Mikhievich
 */
class ReportableRunListener extends AbstractRunListener {

	private final ISpockReporter spockReporter;
	private final IMethodInterceptor fixturesInterceptor;

	@Inject
	public ReportableRunListener(ISpockReporter spockReporter, IMethodInterceptor fixturesInterceptor) {
		checkArgument(spockReporter != null, "Spock reporter shouldn't be null");
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