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
import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.NodeInfo;

import javax.annotation.Nonnull;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Abstract entity for the representation of the metadata for the reportable
 * <i>Spock</i> test item (specification, iteration, etc.)
 *
 * @author Dzmitry Mikhievich
 */
public abstract class NodeFootprint<T extends NodeInfo<?, ? extends AnnotatedElement>> extends ReportableItemFootprint<T> {
	private final List<ReportableItemFootprint<MethodInfo>> fixtures;

	NodeFootprint(@Nonnull T nodeInfo, Maybe<String> id) {
		super(nodeInfo, id);
		fixtures = new ArrayList<>();
	}

	/**
	 * Find unpublished fixture footprint. It used to address an issue, when the node footprint can have multiple fixture
	 * footprint, wrapping the same {@link MethodInfo}
	 *
	 * @param fixture target method info
	 * @return footprint
	 */
	ReportableItemFootprint<MethodInfo> findUnpublishedFixtureFootprint(final MethodInfo fixture) {
		Predicate<ReportableItemFootprint<MethodInfo>> criteria = createFixtureMatchPredicate(fixture).and(IS_NOT_PUBLISHED);
		return getFixtures().stream().filter(criteria).findAny().orElseThrow(NoSuchElementException::new);
	}

	void addFixtureFootprint(FixtureFootprint footprint) {
		fixtures.add(footprint);
	}

	List<ReportableItemFootprint<MethodInfo>> getFixtures() {
		return new ArrayList<>(fixtures);
	}

	private static Predicate<ReportableItemFootprint<MethodInfo>> createFixtureMatchPredicate(final MethodInfo fixture) {
		return footprint -> footprint != null && fixture.equals(footprint.getItem());
	}
}
