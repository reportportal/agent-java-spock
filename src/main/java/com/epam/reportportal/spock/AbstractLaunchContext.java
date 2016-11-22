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

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

/**
 * Context which stores and provides the reporting meta data during the test
 * launch
 *
 * @author Dzmitry Mikhievich
 */
abstract class AbstractLaunchContext {

	private String launchId;
	private AtomicReference<Boolean> launchInProgress = new AtomicReference<Boolean>();

	@Nullable
	String getLaunchId() {
		return launchId;
	}

	void setLaunchId(String launchId) {
		this.launchId = launchId;
	}

	/**
	 * @return true if launch status hadn't been started previously, false
	 *         otherwise
	 */
	boolean tryStartLaunch() {
		return launchInProgress.compareAndSet(null, TRUE);
	}

	/**
	 * @return true if launch status hadn't been started previously, false
	 *         otherwise
	 */
	boolean tryFinishLaunch() {
		return launchInProgress.compareAndSet(TRUE, FALSE);
	}

	boolean isSpecRegistered(SpecInfo specInfo) {
		return findSpecFootprint(specInfo) != null;
	}

	abstract void addRunningSpec(String id, SpecInfo specInfo);

	abstract void addRunningFeature(FeatureInfo featureInfo);

	abstract void addRunningIteration(String id, IterationInfo iterationInfo);

	abstract NodeFootprint<IterationInfo> findIterationFootprint(IterationInfo iterationInfo);

	abstract Iterable<? extends NodeFootprint<IterationInfo>> findIterationFootprints(FeatureInfo featureInfo);

	abstract NodeFootprint<SpecInfo> findSpecFootprint(SpecInfo specInfo);

	abstract Iterable<? extends NodeFootprint<SpecInfo>> findAllUnpublishedSpecFootprints();

	abstract IRuntimePointer getRuntimePointerForSpec(SpecInfo specInfo);

	interface IRuntimePointer {

		FeatureInfo getCurrentFeature();

		IterationInfo getCurrentIteration();
	}
}
