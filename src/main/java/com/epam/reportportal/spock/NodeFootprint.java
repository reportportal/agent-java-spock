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

import static com.epam.reportportal.spock.NodeInfoUtils.getMethodIdentifier;
import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;
import static java.lang.Boolean.valueOf;

import java.util.List;

import javax.annotation.Nullable;

import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.NodeInfo;

import com.google.common.base.Predicate;

/**
 * Created by Dzmitry_Mikhievich
 */
abstract class NodeFootprint<T extends NodeInfo> extends ReportableItemFootprint<T> {

	/*
	 * Approximate fixtures count, which should match the most of cases. This is
	 * a some kind of "happy medium" between memory consumption and potential
	 * performance drawback on arrays coping
	 */
	private static final int APPROXIMATE_CAPACITY = 3;

	private final List<FixtureFootprint> fixtures;

	NodeFootprint(T nodeInfo, String id) {
		super(nodeInfo, id);
		fixtures = newArrayListWithCapacity(APPROXIMATE_CAPACITY);
	}

	List<FixtureFootprint> getFixtures() {
		return newArrayList(fixtures);
	}

	public FixtureFootprint findFixtureFootprint(MethodInfo fixture, @Nullable final Boolean isPublished) {
		final String fullName = getMethodIdentifier(fixture);
		return find(getFixtures(), new Predicate<FixtureFootprint>() {
			@Override
			public boolean apply(@Nullable FixtureFootprint footprint) {
				if (footprint != null) {
					boolean isNameEqual = fullName.equals(footprint.getFullName());
					if (isPublished != null) {
						return isNameEqual && isPublished.equals(valueOf(footprint.isPublished()));
					}
					return isNameEqual;
				}
				return false;
			}
		});
	}

	public void addFixtureFootprint(FixtureFootprint footprint) {
		fixtures.add(footprint);
	}
}
