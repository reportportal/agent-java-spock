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

	private final T nodeInfo;
	private final String id;
	private String status;
	private boolean published = false;

	protected ReportableItemFootprint(T nodeInfo, String id) {
		this.nodeInfo = nodeInfo;
		this.id = id;
	}

	public T getNodeInfo() {
		return nodeInfo;
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
}
