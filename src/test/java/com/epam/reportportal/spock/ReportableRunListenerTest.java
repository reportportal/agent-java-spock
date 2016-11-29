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

import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.model.*;

/**
 * @author Dzmitry Mikhievich
 */
@RunWith(MockitoJUnitRunner.class)
public class ReportableRunListenerTest {

	@Mock
	private ISpockReporter spockReporterMock;
	@Mock
	private IMethodInterceptor fixturesInterceptorMock;
	@InjectMocks
	private ReportableRunListener runListener;

	@Test(expected = IllegalArgumentException.class)
	public void constructor_nullReporter() {
		new ReportableRunListener(null, fixturesInterceptorMock);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_nullFixtureInterceptor() {
		new ReportableRunListener(spockReporterMock, null);
	}

	@Test
	public void beforeSpec() {
		SpecInfo spec = createSpecWithFixtures();
		runListener.beforeSpec(spec);

		verify(spockReporterMock, times(1)).registerSpec(spec);
		assertThat(spec.getAllFixtureMethods(), everyItem(hasInterceptor(fixturesInterceptorMock)));
	}

	@Test
	public void beforeFeature() {
		FeatureInfo feature = new FeatureInfo();
		runListener.beforeFeature(feature);

		verify(spockReporterMock, times(1)).registerFeature(feature);
	}

	@Test
	public void beforeIteration() {
		IterationInfo iteration = mock(IterationInfo.class);
		runListener.beforeIteration(iteration);

		verify(spockReporterMock, times(1)).registerIteration(iteration);
	}

	@Test
	public void afterIteration() {
		IterationInfo iteration = mock(IterationInfo.class);
		runListener.afterIteration(iteration);

		verify(spockReporterMock, times(1)).publishIterationResult(iteration);
	}

	@Test
	public void afterFeature() {
		FeatureInfo feature = new FeatureInfo();
		runListener.afterFeature(feature);

		verify(spockReporterMock, times(1)).publishFeatureResult(feature);
	}

	@Test
	public void afterSpec() {
		SpecInfo spec = new SpecInfo();
		runListener.afterSpec(spec);

		verify(spockReporterMock, times(1)).publishSpecResult(spec);
	}

	@Test
	public void error() {
		ErrorInfo error = mock(ErrorInfo.class);
		runListener.error(error);

		verify(spockReporterMock, times(1)).reportError(error);
	}

	@Test
	public void featureSkipped() {
		FeatureInfo feature = new FeatureInfo();
		runListener.featureSkipped(feature);

		InOrder inOrder = inOrder(spockReporterMock);
		inOrder.verify(spockReporterMock, times(1)).registerFeature(feature);
		inOrder.verify(spockReporterMock, times(1)).trackSkippedFeature(feature);
	}

	@Test
	public void specSkipped() {
		SpecInfo spec = new SpecInfo();
		runListener.specSkipped(spec);

		InOrder inOrder = inOrder(spockReporterMock);
		inOrder.verify(spockReporterMock, times(1)).trackSkippedSpec(spec);
		inOrder.verify(spockReporterMock, times(1)).publishSpecResult(spec);
	}

	private static Matcher<MethodInfo> hasInterceptor(final IMethodInterceptor interceptor) {
		return new BaseMatcher<MethodInfo>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("has interceptor ").appendText(interceptor.toString());
			}

			@Override
			public boolean matches(Object item) {
				if (item instanceof MethodInfo) {
					MethodInfo methodInfo = (MethodInfo) item;
					return methodInfo.getInterceptors().contains(interceptor);
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