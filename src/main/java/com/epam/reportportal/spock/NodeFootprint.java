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

import com.google.common.base.Predicate;
import io.reactivex.Maybe;
import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.NodeInfo;

import java.util.List;

import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;

/**
 * Abstract entity for the representation of the metadata for the reportable
 * <i>Spock</i> test item (specification, iteration, etc.)
 *
 * @author Dzmitry Mikhievich
 */
abstract class NodeFootprint<T extends NodeInfo> extends ReportableItemFootprint<T> {

	/*
	 * Approximate fixtures count, which should match most cases. This is
	 * a kind of "happy medium" between memory consumption and potential
	 * performance drawback on arrays coping
	 */
	private static final int APPROXIMATE_CAPACITY = 4;

	private final List<ReportableItemFootprint<MethodInfo>> fixtures;

	NodeFootprint(T nodeInfo, Maybe<String> id) {
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

	List<ReportableItemFootprint<MethodInfo>> getFixtures() {
		return newArrayList(fixtures);
	}

	private static Predicate<ReportableItemFootprint<MethodInfo>> createFixtureMatchPredicate(final MethodInfo fixture) {
		return footprint -> footprint != null && fixture.equals(footprint.getItem());
	}
}
