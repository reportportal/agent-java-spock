package com.epam.reportportal.spock;

import org.codehaus.groovy.runtime.StackTraceUtils;
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
		spockService.registerFixture(invocation.getMethod());
		try {
			invocation.proceed();
		} catch (Throwable ex) {
			// explicitly report exception to has an ability to track error
			// before result publishing
			spockService.reportFixtureError(new ErrorInfo(invocation.getMethod(), ex));
			throw ex;
		} finally {
			spockService.publishFixtureResult(invocation.getMethod());
		}
	}
}
