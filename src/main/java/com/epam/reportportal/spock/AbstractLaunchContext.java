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

	public abstract void addRunningSpec(SpecInfo specInfo, String id);

	public abstract void addRunningFeature(FeatureInfo featureInfo);

	public abstract void addRunningIteration(IterationInfo iterationInfo, String id);

	public abstract ReportableItemFootprint<IterationInfo> findIterationFootprint(IterationInfo iterationInfo);

	public abstract List<? extends ReportableItemFootprint<IterationInfo>> findIterationFootprints(FeatureInfo featureInfo);

	public abstract ReportableItemFootprint<SpecInfo> findSpecFootprint(SpecInfo specInfo);

	public abstract IRuntimePointer getRuntimePointer();

	public String getLaunchId() {
		return launchId;
	}

	public void setLaunchId(String launchId) {
		this.launchId = launchId;
	}

	public interface IRuntimePointer {

		SpecInfo getCurrentSpec();

		FeatureInfo getCurrentFeature();

		IterationInfo getCurrentIteration();
	}
}
