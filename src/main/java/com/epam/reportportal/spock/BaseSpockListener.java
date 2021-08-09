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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.model.*;

import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Report portal custom event listener. Supports parallel execution of test
 * methods, suites, test classes.
 * Can be extended by providing {@link ISpockService} implementation
 */
public class BaseSpockListener extends AbstractRunListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseSpockListener.class);

	private static final AtomicInteger INSTANCES = new AtomicInteger(0);

	private final ISpockService spockService;

	public BaseSpockListener(ISpockService spockService) {
		checkArgument(spockService != null, "Spock service shouldn't be null");

		this.spockService = spockService;
		if (INSTANCES.incrementAndGet() > 1) {
			final String warning = "WARNING! More than one Spock ReportPortal listener is added";
			LOGGER.warn(warning);

			//even if logger is not configured, print the message to default stdout
			System.out.println(warning);
		}
	}

	public ISpockService getSpockService() {
		return spockService;
	}

	@Override
	public void beforeSpec(SpecInfo spec) {
		spockService.registerSpec(spec);
		for (MethodInfo fixture : spec.getAllFixtureMethods()) {
			fixture.addInterceptor(new FixtureInterceptor(spockService));
		}
	}

	@Override
	public void beforeFeature(FeatureInfo feature) {
		spockService.registerFeature(feature);
	}

	@Override
	public void beforeIteration(IterationInfo iteration) {
		spockService.registerIteration(iteration);
	}

	@Override
	public void afterIteration(IterationInfo iteration) {
		spockService.publishIterationResult(iteration);
	}

	@Override
	public void afterFeature(FeatureInfo feature) {
		spockService.publishFeatureResult(feature);
	}

	@Override
	public void afterSpec(SpecInfo spec) {
		spockService.publishSpecResult(spec);
	}

	@Override
	public void error(ErrorInfo error) {
		spockService.reportError(error);
	}

	@Override
	public void featureSkipped(FeatureInfo feature) {
		spockService.registerFeature(feature);
		spockService.trackSkippedFeature(feature);
	}

	@Override
	public void specSkipped(SpecInfo spec) {
		spockService.trackSkippedSpec(spec);
		spockService.publishSpecResult(spec);
	}

}
