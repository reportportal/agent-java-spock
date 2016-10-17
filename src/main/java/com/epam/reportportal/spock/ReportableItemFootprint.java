package com.epam.reportportal.spock;

import org.spockframework.runtime.model.NodeInfo;

/**
 * @author Dzmitry Mikhievich
 */
abstract class ReportableItemFootprint<T extends NodeInfo> {

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

	public String getStatus() {
		return status;
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
