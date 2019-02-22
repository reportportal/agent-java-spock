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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.BatchedReportPortalService;

import java.lang.reflect.Proxy;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Dzmitry Mikhievich
 */
@RunWith(MockitoJUnitRunner.class)
public class SpockReporterProviderTest {

	@Mock
	private BatchedReportPortalService spockReporterMock;
	@Mock
	private ListenerParameters parametersMock;
	@Mock
	private AbstractLaunchContext launchContext;

	@Test(expected = IllegalArgumentException.class)
	public void constructor_rpServiceIsNull() {
		new SpockReporterProvider(null, parametersMock, launchContext);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_parametersArgIsNull() {
		new SpockReporterProvider(spockReporterMock, null, launchContext);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_launchContextIsNull() {
		new SpockReporterProvider(spockReporterMock, parametersMock, null);
	}

    @Test
    public void get_reportingIsEnabled() throws IllegalAccessException {
        when(parametersMock.getEnable()).thenReturn(TRUE);
        SpockReporterProvider provider = new SpockReporterProvider(spockReporterMock, parametersMock, launchContext);

        ISpockReporter reporter = provider.get();

        assertThat(reporter, instanceOf(SpockReporter.class));
        assertThat(((BatchedReportPortalService)readField(reporter, "reportPortalService", true)), equalTo(spockReporterMock));
        assertThat(((ListenerParameters)readField(reporter, "launchParameters", true)), equalTo(parametersMock));
        assertThat(((AbstractLaunchContext)readField(reporter, "launchContext", true)), equalTo(launchContext));
    }

    @Test
    public void get_reportingIsDisabled() {
        when(parametersMock.getEnable()).thenReturn(FALSE);
        SpockReporterProvider provider = new SpockReporterProvider(spockReporterMock, parametersMock, launchContext);
        ISpockReporter reporter = provider.get();

		//Check if stub is returned
		assertThat(reporter, instanceOf(Proxy.class));
    }

}