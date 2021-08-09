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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.model.*;

import java.util.List;

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author Dzmitry Mikhievich
 */
public class BaseSpockListenerTest {
	private SpockService spockServiceMock;

	private BaseSpockListener runListener;

	@BeforeEach
	public void init() {
		spockServiceMock = Mockito.mock(SpockService.class);
		runListener = new BaseSpockListener(spockServiceMock);
	}

	@Test
	public void constructor_nullReporter() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new BaseSpockListener(null));
	}

	@Test
	public void beforeSpec() {
		SpecInfo spec = createSpecWithFixtures();
		runListener.beforeSpec(spec);

		verify(spockServiceMock, times(1)).registerSpec(spec);
		assertThat(spec.getAllFixtureMethods(), everyItem(hasInterceptor()));
	}

	@Test
	public void beforeFeature() {
		FeatureInfo feature = new FeatureInfo();
		runListener.beforeFeature(feature);

		verify(spockServiceMock, times(1)).registerFeature(feature);
	}

	@Test
	public void beforeIteration() {
		IterationInfo iteration = mock(IterationInfo.class);
		runListener.beforeIteration(iteration);

		verify(spockServiceMock, times(1)).registerIteration(iteration);
	}

	@Test
	public void afterIteration() {
		IterationInfo iteration = mock(IterationInfo.class);
		runListener.afterIteration(iteration);

		verify(spockServiceMock, times(1)).publishIterationResult(iteration);
	}

	@Test
	public void afterFeature() {
		FeatureInfo feature = new FeatureInfo();
		runListener.afterFeature(feature);

		verify(spockServiceMock, times(1)).publishFeatureResult(feature);
	}

	@Test
	public void afterSpec() {
		SpecInfo spec = new SpecInfo();
		runListener.afterSpec(spec);

		verify(spockServiceMock, times(1)).publishSpecResult(spec);
	}

	@Test
	public void error() {
		ErrorInfo error = mock(ErrorInfo.class);
		runListener.error(error);

		verify(spockServiceMock, times(1)).reportError(error);
	}

	@Test
	public void featureSkipped() {
		FeatureInfo feature = new FeatureInfo();
		runListener.featureSkipped(feature);

		InOrder inOrder = inOrder(spockServiceMock);
		inOrder.verify(spockServiceMock, times(1)).registerFeature(feature);
		inOrder.verify(spockServiceMock, times(1)).trackSkippedFeature(feature);
	}

	@Test
	public void specSkipped() {
		SpecInfo spec = new SpecInfo();
		runListener.specSkipped(spec);

		InOrder inOrder = inOrder(spockServiceMock);
		inOrder.verify(spockServiceMock, times(1)).trackSkippedSpec(spec);
		inOrder.verify(spockServiceMock, times(1)).publishSpecResult(spec);
	}

	private static Matcher<MethodInfo> hasInterceptor() {
		return new BaseMatcher<MethodInfo>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("has Fixture Interceptor");
			}

			@Override
			public boolean matches(Object item) {
				if (item instanceof MethodInfo) {
					MethodInfo methodInfo = (MethodInfo) item;

					Boolean countGreaterThanZero = false;
					Boolean classNameMatches = false;
					List<IMethodInterceptor> interceptorList = methodInfo.getInterceptors();
					if (interceptorList.size() > 0) {
						countGreaterThanZero = true;
						for (IMethodInterceptor methodInterceptor : interceptorList) {
							String interceptorName = methodInterceptor.getClass().getName();
							classNameMatches = classNameMatches || interceptorName.toLowerCase().contains("fixtureinterceptor");
						}
					}
					return countGreaterThanZero && classNameMatches;
				}
				return false;
			}
		};
	}

	private static SpecInfo createSpecWithFixtures() {
		SpecInfo spec = new SpecInfo();
		spec.addSetupSpecMethod(new MethodInfo());
		spec.addCleanupSpecMethod(new MethodInfo());
		spec.addSetupMethod(new MethodInfo());
		spec.addCleanupMethod(new MethodInfo());
		return spec;
	}

}