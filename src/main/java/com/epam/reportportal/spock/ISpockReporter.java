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

import org.spockframework.runtime.model.*;

/**
 * @author Dzmitry Mikhievich
 */
interface ISpockReporter {

	void startLaunch();

	void registerFixture(MethodInfo fixture);

	void registerSpec(SpecInfo spec);

	void registerFeature(FeatureInfo feature);

	void registerIteration(IterationInfo iteration);

	void trackSkippedFeature(FeatureInfo featureInfo);

	void trackSkippedSpec(SpecInfo spec);

	void publishFixtureResult(MethodInfo fixture);

	void publishIterationResult(IterationInfo iteration);

	void publishFeatureResult(FeatureInfo feature);

	void publishSpecResult(SpecInfo spec);

	void reportError(ErrorInfo errorInfo);

	void finishLaunch();
}
