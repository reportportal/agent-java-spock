package com.epam.reportportal.spock;

import static com.google.common.base.Preconditions.checkArgument;

import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.ErrorInfo;

/**
 * Created by Dzmitry_Mikhievich
 */
class FixtureInterceptor implements IMethodInterceptor {

	private final ISpockReporter spockReporter;

	public FixtureInterceptor(ISpockReporter spockReporter) {
		checkArgument(spockReporter != null);
		this.spockReporter = spockReporter;
	}

	@Override
	public void intercept(IMethodInvocation invocation) throws Throwable {
		spockReporter.registerFixture(invocation.getMethod());
		try {
			invocation.proceed();
		} catch (Throwable ex) {
          //explicitly report exception to has an ability to track error before result publishing
			spockReporter.reportError(new ErrorInfo(invocation.getMethod(), ex));
			throw ex;
		} finally {
			spockReporter.publishFixtureResult(invocation.getMethod());
		}
	}

}
