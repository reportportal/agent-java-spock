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

import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.ErrorInfo;

/**
 * Implementation of {@link org.spockframework.runtime.extension.IMethodInterceptor}, which allows to report
 * fixture method execution
 *
 * @author Dzmitry Mikhievich
 */
class FixtureInterceptor implements IMethodInterceptor {

	private final ISpockReporter spockReporter;

	FixtureInterceptor(ISpockReporter spockReporter) {
		checkArgument(spockReporter != null);

		this.spockReporter = spockReporter;
	}

	@Override
	public void intercept(IMethodInvocation invocation) throws Throwable {
		spockReporter.registerFixture(invocation.getMethod());
		try {
			invocation.proceed();
		} catch (Throwable ex) {
			// explicitly report exception to has an ability to track error
			// before result publishing
			spockReporter.reportError(new ErrorInfo(invocation.getMethod(), ex));
			throw ex;
		} finally {
			spockReporter.publishFixtureResult(invocation.getMethod());
		}
	}

}
