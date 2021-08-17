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

import com.google.common.collect.Lists;
import io.reactivex.Maybe;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.epam.reportportal.spock.NodeInfoUtils.getSpecIdentifier;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
import static java.util.Optional.ofNullable;

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
	public void addRunningFeature(@Nullable Maybe<String> id, @Nonnull FeatureInfo featureInfo) {
		SpecInfo specInfo = featureInfo.getSpec();
		Specification specFootprint = findSpecFootprint(featureInfo.getSpec());
		if (specFootprint != null) {
			getRuntimePointerForSpec(specInfo).setFeatureInfo(featureInfo);
			specFootprint.addRunningFeature(featureInfo, id);
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
	@Nullable
	public NodeFootprint<FeatureInfo> findFeatureFootprint(FeatureInfo featureInfo) {
		return ofNullable(findSpecFootprint(featureInfo.getSpec())).map(s->s.getFeature(featureInfo)).orElse(null);
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
	public Iterable<Iteration> findIterationFootprints(final FeatureInfo featureInfo) {
		return ofNullable(findSpecFootprint(featureInfo.getSpec())).map(s -> s.getFeature(featureInfo))
				.map(Feature::getAllTrackedIteration)
				.orElse(null);
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

		private void addRunningFeature(FeatureInfo featureInfo, Maybe<String> id) {
			getAllTrackedFeatures().add(new Feature(featureInfo, id));
		}

		@Nullable
		private Feature getFeature(final FeatureInfo featureInfo) {
			return getAllTrackedFeatures().stream()
					.filter(input -> input != null && featureInfo.equals(input.getItem()))
					.findAny()
					.orElse(null);
		}

		private List<Feature> getAllTrackedFeatures() {
			if (features == null) {
				features = Lists.newArrayList();
			}
			return features;
		}
	}

	private static class Feature extends NodeFootprint<FeatureInfo> {
		private List<Iteration> iterations;

		Feature(FeatureInfo featureInfo, Maybe<String> id) {
			super(featureInfo, id);
		}

		@Override
		boolean hasDescendants() {
			return false;
		}

		private List<Iteration> getAllTrackedIteration() {
			if (iterations == null) {
				iterations = Lists.newArrayList();
			}
			return iterations;
		}

		private Iteration getIteration(final IterationInfo iterationInfo) {
			return getAllTrackedIteration().stream()
					.filter(Objects::nonNull)
					.filter(input -> iterationInfo.equals(input.getItem()))
					.findAny()
					.orElse(null);
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
