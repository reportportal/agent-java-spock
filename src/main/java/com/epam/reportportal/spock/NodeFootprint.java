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

import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;

import java.util.List;

import javax.annotation.Nullable;

import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.NodeInfo;

import com.google.common.base.Predicate;

/**
 * Abstract entity for the representation of the metadata for the reportable
 * <i>Spock</i> test item (specification, iteration, etc.)
 *
 * @author Dzmitry Mikhievich
 */
abstract class NodeFootprint<T extends NodeInfo> extends ReportableItemFootprint<T> {

	/*
	 * Approximate fixtures count, which should match the most of cases. This is
	 * a some kind of "happy medium" between memory consumption and potential
	 * performance drawback on arrays coping
	 */
	private static final int APPROXIMATE_CAPACITY = 4;

	private final List<ReportableItemFootprint<MethodInfo>> fixtures;

	NodeFootprint(T nodeInfo, String id) {
		super(nodeInfo, id);
		fixtures = newArrayListWithCapacity(APPROXIMATE_CAPACITY);
	}


	ReportableItemFootprint<MethodInfo> findFixtureFootprint(final MethodInfo fixture) {
		Predicate<ReportableItemFootprint<MethodInfo>> criteria = createFixtureMatchPredicate(fixture);
		return find(getFixtures(), criteria);
	}

	/**
	 * Find unpublished fixture footprint. It used to address an issue, when the node footprint can have multiple fixture
	 * footprint, wrapping the same {@link MethodInfo}
	 * 
	 * @param fixture target method info
	 * @return footprint
	 */
	ReportableItemFootprint<MethodInfo> findUnpublishedFixtureFootprint(final MethodInfo fixture) {
		Predicate<ReportableItemFootprint<MethodInfo>> criteria = and(createFixtureMatchPredicate(fixture), IS_NOT_PUBLISHED);
		return find(getFixtures(), criteria);
	}

	void addFixtureFootprint(FixtureFootprint footprint) {
		fixtures.add(footprint);
	}

	private List<ReportableItemFootprint<MethodInfo>> getFixtures() {
		return newArrayList(fixtures);
	}

	private static Predicate<ReportableItemFootprint<MethodInfo>> createFixtureMatchPredicate(final MethodInfo fixture) {
		return new Predicate<ReportableItemFootprint<MethodInfo>>() {
			@Override
			public boolean apply(@Nullable ReportableItemFootprint<MethodInfo> footprint) {
				return footprint != null && fixture.equals(footprint.getItem());
			}
		};
	}
}
