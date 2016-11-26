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