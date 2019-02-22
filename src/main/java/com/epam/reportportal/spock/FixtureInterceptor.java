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
