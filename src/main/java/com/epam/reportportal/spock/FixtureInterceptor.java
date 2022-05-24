package com.epam.reportportal.spock;

import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.ErrorInfo;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link org.spockframework.runtime.extension.IMethodInterceptor}, which allows to report
 * fixture method execution
 *
 * @author Dzmitry Mikhievich
 */
class FixtureInterceptor implements IMethodInterceptor {

	private final ReportPortalSpockListener spockService;

	FixtureInterceptor(@Nonnull final ReportPortalSpockListener spockService) {
		this.spockService = spockService;
	}

	@Override
	public void intercept(IMethodInvocation invocation) throws Throwable {
		spockService.registerFixture(invocation.getSpec(), invocation.getFeature(), invocation.getIteration(), invocation.getMethod());
		Throwable exception = null;
		try {
			invocation.proceed();
		} catch (Throwable ex) {
			exception = ex;
			// explicitly report exception to has an ability to track error
			// before result publishing
			spockService.reportFixtureError(invocation.getSpec(),
					invocation.getFeature(),
					invocation.getIteration(),
					new ErrorInfo(invocation.getMethod(), ex)
			);
		}
		spockService.publishFixtureResult(invocation.getSpec(), invocation.getFeature(), invocation.getIteration(), invocation.getMethod());
		if (exception != null) {
			throw exception;
		}
	}
}
