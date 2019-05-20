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

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.util.concurrent.atomic.AtomicReference;
import io.reactivex.Maybe;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;
import org.spockframework.util.Nullable;

/**
 * Context which stores and provides the reporting meta data during the test
 * launch
 *
 * @author Dzmitry Mikhievich
 */
abstract class AbstractLaunchContext {

    private Maybe<String> launchId;
    private AtomicReference<Boolean> launchInProgress = new AtomicReference<Boolean>();

    @Nullable
    Maybe<String> getLaunchId() {
        return launchId;
    }

    void setLaunchId(Maybe<String> launchId) {
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

    abstract void addRunningSpec(Maybe<String> id, SpecInfo specInfo);

    abstract void addRunningFeature(FeatureInfo featureInfo);

    abstract void addRunningIteration(Maybe<String> id, IterationInfo iterationInfo);

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
