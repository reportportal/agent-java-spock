package com.epam.reportportal.spock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.inject.Inject;
import javax.inject.Provider;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.BatchedReportPortalService;

/**
 * @author Dzmitry Mikhievich
 */
class SpockReporterProvider implements Provider<ISpockReporter> {

	private final BatchedReportPortalService reportPortalService;
	private final ListenerParameters parameters;
	private final AbstractLaunchContext launchContext;

	@Inject
	public SpockReporterProvider(BatchedReportPortalService reportPortalService, ListenerParameters parameters,
			AbstractLaunchContext launchContext) {
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
