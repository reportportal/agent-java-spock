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

import io.reactivex.Maybe;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.epam.reportportal.spock.NodeInfoUtils.getSpecIdentifier;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;


/**
 * Default implementation of {@link AbstractLaunchContext}
 *
 * @author Dzmitry Mikhievich
 */
class LaunchContextImpl extends AbstractLaunchContext {

    private final Map<String, Specification> specFootprintsRegistry = new ConcurrentHashMap<>();
    private final Map<String, RuntimePointer> runtimePointersRegistry = new ConcurrentHashMap<>();

    @Override
    public void addRunningSpec(Maybe<String> id, SpecInfo specInfo) {
        Specification specFootprint = new Specification(specInfo, id);
        String specIdentifier = getSpecIdentifier(specInfo);
        runtimePointersRegistry.put(specIdentifier, new RuntimePointer());
        specFootprintsRegistry.put(specIdentifier, specFootprint);
    }

    @Override
    public void addRunningFeature(FeatureInfo featureInfo) {
        SpecInfo specInfo = featureInfo.getSpec();
        Specification specFootprint = findSpecFootprint(featureInfo.getSpec());
        if (specFootprint != null) {
            getRuntimePointerForSpec(specInfo).setFeatureInfo(featureInfo);
            specFootprint.addRunningFeature(featureInfo);
        }
    }

    @Override
    public void addRunningIteration(Maybe<String> id, IterationInfo iterationInfo) {
        SpecInfo specInfo = iterationInfo.getFeature().getSpec();
        Specification specification = findSpecFootprint(specInfo);
        if (specification != null) {
            Feature feature = specification.getFeature(iterationInfo.getFeature());
            if (feature != null) {
                getRuntimePointerForSpec(specInfo).setIterationInfo(iterationInfo);
                feature.addIteration(iterationInfo, id);
            }
        }
    }

    @Override
    public NodeFootprint<IterationInfo> findIterationFootprint(IterationInfo iterationInfo) {
        Specification specFootprint = findSpecFootprint(iterationInfo.getFeature().getSpec());
        if (specFootprint != null) {
            Feature feature = specFootprint.getFeature(iterationInfo.getFeature());
            if (feature != null) {
                return feature.getIteration(iterationInfo);
            }
        }
        return null;
    }

    @Override
    public Iterable<Iteration> findIterationFootprints(FeatureInfo featureInfo) {
        Specification specFootprint = findSpecFootprint(featureInfo.getSpec());
        if (specFootprint != null) {
            return specFootprint.getFeature(featureInfo).getAllTrackedIteration();
        }
        return null;
    }

    @Override
    public Specification findSpecFootprint(final SpecInfo specInfo) {
        return findValueInRegistry(specFootprintsRegistry, specInfo);
    }

    @Override
    public Iterable<Specification> findAllUnpublishedSpecFootprints() {
        return specFootprintsRegistry.values().stream().filter(IS_NOT_PUBLISHED).collect(Collectors.toList());
    }

    @Override
    public RuntimePointer getRuntimePointerForSpec(SpecInfo specInfo) {
        return findValueInRegistry(runtimePointersRegistry, specInfo);
    }

    private <T> T findValueInRegistry(Map<String, T> registry, SpecInfo specInfo) {
        T value = null;
        SpecInfo specToFind = specInfo;
        while (value == null && specToFind != null) {
            value = registry.get(getSpecIdentifier(specToFind));
            specToFind = specToFind.getSubSpec();
        }
        return value;
    }

    private static class Specification extends NodeFootprint<SpecInfo> {

        private List<Feature> features;

        Specification(SpecInfo nodeInfo, Maybe<String> id) {
            super(nodeInfo, id);
        }

        @Override
        public boolean hasDescendants() {
            return true;
        }

        private void addRunningFeature(FeatureInfo featureInfo) {
            getAllTrackedFeatures().add(new Feature(featureInfo));
        }

        private Feature getFeature(final FeatureInfo featureInfo) {
            return Iterables.find(getAllTrackedFeatures(), input -> input != null && featureInfo.equals(input.featureInfo));
        }

        private List<Feature> getAllTrackedFeatures() {
            if (features == null) {
                features = Lists.newArrayList();
            }
            return features;
        }
    }

    private static class Feature {

        private final FeatureInfo featureInfo;
        private List<Iteration> iterations;

        Feature(FeatureInfo featureInfo) {
            this.featureInfo = featureInfo;
        }

        private List<Iteration> getAllTrackedIteration() {
            if (iterations == null) {
                iterations = Lists.newArrayList();
            }
            return iterations;
        }

        private Iteration getIteration(final IterationInfo iterationInfo) {

            return Iterables.find(getAllTrackedIteration(), input -> input != null && iterationInfo.equals(input.getItem()));
        }

        private void addIteration(IterationInfo iterationInfo, Maybe<String> id) {
            getAllTrackedIteration().add(new Iteration(iterationInfo, id));
        }
    }

    private static class Iteration extends NodeFootprint<IterationInfo> {

        Iteration(IterationInfo nodeInfo, Maybe<String> id) {
            super(nodeInfo, id);
        }

        @Override
        public boolean hasDescendants() {
            return false;
        }
    }

    private static class RuntimePointer implements IRuntimePointer {

        private FeatureInfo featureInfo;
        private IterationInfo iterationInfo;

        private void setFeatureInfo(FeatureInfo featureInfo) {
            this.featureInfo = featureInfo;
        }

        private void setIterationInfo(IterationInfo iterationInfo) {
            this.iterationInfo = iterationInfo;
        }

        @Override
        public FeatureInfo getCurrentFeature() {
            return featureInfo;
        }

        @Override
        public IterationInfo getCurrentIteration() {
            return iterationInfo;
        }
    }
}
