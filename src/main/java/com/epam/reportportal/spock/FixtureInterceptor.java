package com.epam.reportportal.spock;

import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.ErrorInfo;

import static rp.com.google.common.base.Preconditions.checkArgument;


/**
 * Implementation of {@link org.spockframework.runtime.extension.IMethodInterceptor}, which allows to report
 * fixture method execution
 *
 * @author Dzmitry Mikhievich
 */
class FixtureInterceptor implements IMethodInterceptor {

    private final ISpockService spockService;

    FixtureInterceptor(ISpockService spockService) {
        checkArgument(spockService != null);

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
            spockService.reportError(new ErrorInfo(invocation.getMethod(), ex));
            throw ex;
        } finally {
            spockService.publishFixtureResult(invocation.getMethod());
        }
    }
}