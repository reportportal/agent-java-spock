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

import org.spockframework.runtime.model.*;

/**
 * Provides the API for the reporting of Spock tests
 *
 * @author Dzmitry Mikhievich
 */
interface ISpockReporter {

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
