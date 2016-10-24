package com.epam.reportportal.spock;

import static com.epam.reportportal.spock.NodeInfoUtils.getMethodIdentifier;

import org.spockframework.runtime.model.MethodInfo;

/**
 * Created by Dzmitry_Mikhievich
 */
class FixtureFootprint extends ReportableItemFootprint<MethodInfo> {

	private final String fullName;

	protected FixtureFootprint(MethodInfo item, String id) {
		super(item, id);
		this.fullName = getMethodIdentifier(getItem());
	}

	public String getFullName() {
		return fullName;
	}

	@Override
	public boolean hasDescendants() {
		return false;
	}
}
