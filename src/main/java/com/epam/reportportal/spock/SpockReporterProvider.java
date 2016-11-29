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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.inject.Inject;
import javax.inject.Provider;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.BatchedReportPortalService;

/**
 * Implementation of the {@link javax.inject.Provider}, which is responsible
 * for the provisioning of {@link ISpockReporter} instance
 *
 * @author Dzmitry Mikhievich
 */
class SpockReporterProvider implements Provider<ISpockReporter> {

	private final BatchedReportPortalService reportPortalService;
	private final ListenerParameters parameters;
	private final AbstractLaunchContext launchContext;

	@Inject
	public SpockReporterProvider(BatchedReportPortalService reportPortalService, ListenerParameters parameters,
			AbstractLaunchContext launchContext) {
		checkArgument(reportPortalService != null, "Report portal service shouldn't be null");
		checkArgument(parameters != null, "Launch parameters shouldn't be null");
		checkArgument(launchContext != null, "Launch context shouldn't be null");

		this.reportPortalService = reportPortalService;
		this.parameters = parameters;
		this.launchContext = launchContext;
	}

	@Override
	public ISpockReporter get() {
		if (parameters.getEnable()) {
			return new SpockReporter(reportPortalService, parameters, launchContext);
		}
		return (ISpockReporter) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[] { ISpockReporter.class },
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						return null;
					}
				});
	}
}
