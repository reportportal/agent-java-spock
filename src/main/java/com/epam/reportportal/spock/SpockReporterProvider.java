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
