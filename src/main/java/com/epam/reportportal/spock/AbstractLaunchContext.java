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

import java.util.List;

import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * @author Dzmitry Mikhievich
 */
abstract class AbstractLaunchContext {

	private String launchId;

	public String getLaunchId() {
		return launchId;
	}

	public void setLaunchId(String launchId) {
		this.launchId = launchId;
	}

	public boolean isSpecRegistered(SpecInfo specInfo) {
		return findSpecFootprint(specInfo) != null;
	}

	public abstract void addRunningSpec(String id, SpecInfo specInfo);

	public abstract void addRunningFeature(FeatureInfo featureInfo);

	public abstract void addRunningIteration(String id, IterationInfo iterationInfo);

	public abstract NodeFootprint<IterationInfo> findIterationFootprint(IterationInfo iterationInfo);

	public abstract Iterable<? extends NodeFootprint<IterationInfo>> findIterationFootprints(FeatureInfo featureInfo);

	public abstract NodeFootprint<SpecInfo> findSpecFootprint(SpecInfo specInfo);

	public abstract Iterable<? extends NodeFootprint<SpecInfo>> findAllUnpublishedSpecFootprints();

	public abstract IRuntimePointer getRuntimePointer();

	public interface IRuntimePointer {

		SpecInfo getCurrentSpec();

		FeatureInfo getCurrentFeature();

		IterationInfo getCurrentIteration();
	}
}
