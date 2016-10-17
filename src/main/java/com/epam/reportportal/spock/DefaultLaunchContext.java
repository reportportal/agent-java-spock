package com.epam.reportportal.spock;

import static com.google.common.base.Strings.nullToEmpty;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Default non-thread safe implementation of AbstractLaunchContext
 * <p/>
 * 
 * @author Dzmitry Mikhievich
 */
class DefaultLaunchContext extends AbstractLaunchContext {

	private static final Function<SpecInfo, String> SPEC_ID_EXTRACTOR = new Function<SpecInfo, String>() {
		// TODO refactor
		@Nullable
		@Override
		public String apply(@Nullable SpecInfo spec) {
			if (spec != null) {
				return nullToEmpty(spec.getPackage()) + "." + spec.getFilename();
			}
			return "";
		}
	};

	private final RuntimePointer pointer = new RuntimePointer();
	private final Map<String, Specification> specsRegistry = new ConcurrentHashMap<String, Specification>();

	@Override
	public void addRunningSpec(SpecInfo specInfo, String id) {
		Specification specification = new Specification(specInfo, id);
		pointer.setSpecInfo(specInfo);
		specsRegistry.put(SPEC_ID_EXTRACTOR.apply(specInfo), specification);
	}

	@Override
	public void addRunningFeature(FeatureInfo featureInfo) {
		Specification specification = findSpecFootprint(featureInfo.getSpec());
		if (specification != null) {
			pointer.setFeatureInfo(featureInfo);
			specification.addRunningFeature(featureInfo);
		}
	}

	@Override
	public void addRunningIteration(IterationInfo iterationInfo, String id) {
		Specification specification = findSpecFootprint(iterationInfo.getFeature().getSpec());
		if (specification != null) {
			Feature feature = specification.getFeature(iterationInfo.getFeature());
			if (feature != null) {
				pointer.setIterationInfo(iterationInfo);
				feature.addIteration(iterationInfo, id);
			}
		}
	}

	@Override
	public ReportableItemFootprint<IterationInfo> findIterationFootprint(IterationInfo iterationInfo) {
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
	public List<? extends ReportableItemFootprint<IterationInfo>> findIterationFootprints(FeatureInfo featureInfo) {
		Specification specification = findSpecFootprint(featureInfo.getSpec());
		if (specification != null) {
			return specification.getFeature(featureInfo).getAllTrackedIteration();
		}
		return null;
	}

	@Override
	public Specification findSpecFootprint(final SpecInfo specInfo) {
		return specsRegistry.get(SPEC_ID_EXTRACTOR.apply(specInfo));
	}

	@Override
	public IRuntimePointer getRuntimePointer() {
		return pointer;
	}

	private static class Specification extends ReportableItemFootprint<SpecInfo> {

		private List<Feature> features;

		Specification(SpecInfo nodeInfo, String id) {
			super(nodeInfo, id);
		}

		public void addRunningFeature(FeatureInfo featureInfo) {
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

		private FeatureInfo featureInfo;
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
					return input != null && iterationInfo.equals(input.getNodeInfo());
				}
			});
		}

		private void addIteration(IterationInfo iterationInfo, String id) {
			getAllTrackedIteration().add(new Iteration(iterationInfo, id));
		}
	}

	private static class Iteration extends ReportableItemFootprint<IterationInfo> {

		Iteration(IterationInfo nodeInfo, String id) {
			super(nodeInfo, id);
		}
	}

	private static class RuntimePointer implements IRuntimePointer {

		private SpecInfo specInfo;
		private FeatureInfo featureInfo;
		private IterationInfo iterationInfo;

		public void setSpecInfo(SpecInfo specInfo) {
			this.specInfo = specInfo;
		}

		public void setFeatureInfo(FeatureInfo featureInfo) {
			this.featureInfo = featureInfo;
		}

		public void setIterationInfo(IterationInfo iterationInfo) {
			this.iterationInfo = iterationInfo;
		}

		@Override
		public SpecInfo getCurrentSpec() {
			return specInfo;
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
