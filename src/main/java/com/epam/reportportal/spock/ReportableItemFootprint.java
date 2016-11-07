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

import static com.google.common.base.Preconditions.checkArgument;

import javax.annotation.Nullable;

import org.spockframework.runtime.model.NodeInfo;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

/**
 * @author Dzmitry Mikhievich
 */
abstract class ReportableItemFootprint<T extends NodeInfo> {

	static final Predicate<ReportableItemFootprint> IS_PUBLISHED_CONDITION = new Predicate<ReportableItemFootprint>() {
		@Override
		public boolean apply(@Nullable ReportableItemFootprint input) {
			return input != null && !input.isPublished();
		}
	};

	private final String id;
	private final T item;

	private String status;
	private boolean published = false;

	ReportableItemFootprint(T item, String id) {
		checkArgument(item != null, "Node info shouldn't be null");
		checkArgument(id != null, "Test item id shouldn't be null");
		this.id = id;
		this.item = item;
	}

	public T getItem() {
		return item;
	}

	public String getId() {
		return id;
	}

	public Optional<String> getStatus() {
		return Optional.fromNullable(status);
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void markAsPublished() {
		this.published = true;
	}

	public boolean isPublished() {
		return published;
	}

	public String getItemName() {
		return getItem().getName();
	}

	public abstract boolean hasDescendants();
}
