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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.ErrorInfo;
import org.spockframework.runtime.model.MethodInfo;

import static org.mockito.Mockito.*;

/**
 * @author Dzmitry Mikhievich
 */
public class FixtureInterceptorTest {

	private static final MethodInfo testFixture = new MethodInfo();

	@Mock
	private IMethodInvocation invocationMock;
	@Mock
	private ISpockService spockServiceMock;
	@InjectMocks
	private FixtureInterceptor fixtureInterceptor;

	@Test
	public void constructor_spockReporterIsNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new FixtureInterceptor(null));
	}

	@Test
	public void testIntercept_whenMethodInvocationThrowsException_fixtureShouldBeReportedCorrectly() throws Throwable {
		when(invocationMock.getMethod()).thenReturn(testFixture);

		doThrow(TestException.class).when(invocationMock).proceed();

		try {
			fixtureInterceptor.intercept(invocationMock);
		} catch (Throwable ignore) {
		}

		InOrder inOrder = inOrder(spockServiceMock);
		inOrder.verify(spockServiceMock, times(1)).registerFixture(testFixture);
		inOrder.verify(spockServiceMock, times(1)).reportError(Mockito.<ErrorInfo>any());
		inOrder.verify(spockServiceMock, times(1)).publishFixtureResult(testFixture);
	}

	@Test
	public void testIntercept_whenMethodInvocationThrowsException_exceptionShouldBeThrownAgain() throws Throwable {
		when(invocationMock.getMethod()).thenReturn(testFixture);

		doThrow(TestException.class).when(invocationMock).proceed();

		Assertions.assertThrows(TestException.class, () -> fixtureInterceptor.intercept(invocationMock));
	}

	@Test
	public void testIntercept_methodInvocationProceedSuccessfully() throws Throwable {
		when(invocationMock.getMethod()).thenReturn(testFixture);

		fixtureInterceptor.intercept(invocationMock);

		InOrder inOrder = inOrder(spockServiceMock);
		inOrder.verify(spockServiceMock, times(1)).registerFixture(testFixture);
		inOrder.verify(spockServiceMock, times(1)).publishFixtureResult(testFixture);
		verify(spockServiceMock, times(0)).reportError(Mockito.<ErrorInfo>any());
	}

	private static class TestException extends Throwable {
	}
}