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

import static com.epam.reportportal.spock.NodeInfoUtils.getSpecIdentifier;
import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
import static com.google.common.collect.Iterables.filter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Default implementation of {@link AbstractLaunchContext}
 *
 * @author Dzmitry Mikhievich
 */
class LaunchContextImpl extends AbstractLaunchContext {

	private final Map<String, Specification> specFootprintsRegistry = new ConcurrentHashMap<String, Specification>();
	private final Map<String, RuntimePointer> runtimePointersRegistry = new ConcurrentHashMap<String, RuntimePointer>();

	@Override
	public void addRunningSpec(String id, SpecInfo specInfo) {
		Specification specification = new Specification(specInfo, id);
		String specIdentifier = getSpecIdentifier(specInfo);
		runtimePointersRegistry.put(specIdentifier, new RuntimePointer());
		specFootprintsRegistry.put(specIdentifier, specification);
	}

	@Override
	public void addRunningFeature(FeatureInfo featureInfo) {
		SpecInfo specInfo = featureInfo.getSpec();
		Specification specification = findSpecFootprint(featureInfo.getSpec());
		if (specification != null) {
			getRuntimePointerForSpec(specInfo).setFeatureInfo(featureInfo);
			specification.addRunningFeature(featureInfo);
		}
	}

	@Override
	public void addRunningIteration(String id, IterationInfo iterationInfo) {
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
		Specification specification = findSpecFootprint(iterationInfo.getFeature().getSpec());
		if (specification != null) {
			Feature feature = specification.getFeature(iterationInfo.getFeature());
			if (feature != null) {
				return feature.getIteration(iterationInfo);
			}
		}
		return null;
	}

	@Override
	public Iterable<Iteration> findIterationFootprints(FeatureInfo featureInfo) {
		Specification specification = findSpecFootprint(featureInfo.getSpec());
		if (specification != null) {
			return specification.getFeature(featureInfo).getAllTrackedIteration();
		}
		return null;
	}

	@Override
	public Specification findSpecFootprint(final SpecInfo specInfo) {
		return findValueInRegistry(specFootprintsRegistry, specInfo);
	}

	@Override
	public Iterable<Specification> findAllUnpublishedSpecFootprints() {
		return filter(specFootprintsRegistry.values(), IS_NOT_PUBLISHED);
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

		Specification(SpecInfo nodeInfo, String id) {
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
			return Iterables.find(getAllTrackedFeatures(), new Predicate<Feature>() {
				@Override
				public boolean apply(Feature input) {
					return input != null && featureInfo.equals(input.featureInfo);
				}
			});
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

			return Iterables.find(getAllTrackedIteration(), new Predicate<Iteration>() {
				@Override
				public boolean apply(Iteration input) {
					return input != null && iterationInfo.equals(input.getItem());
				}
			});
		}

		private void addIteration(IterationInfo iterationInfo, String id) {
			getAllTrackedIteration().add(new Iteration(iterationInfo, id));
		}
	}

	private static class Iteration extends NodeFootprint<IterationInfo> {

		Iteration(IterationInfo nodeInfo, String id) {
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
