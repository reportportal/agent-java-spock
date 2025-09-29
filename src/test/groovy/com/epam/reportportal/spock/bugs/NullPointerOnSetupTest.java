package com.epam.reportportal.spock.bugs;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.bugs.NullPointerOnSetupSpec;
import com.epam.reportportal.spock.utils.TestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

public class NullPointerOnSetupTest {

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		ListenerParameters parameters = standardParameters();
		parameters.setEnable(false);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, parameters, testExecutor()));
	}

	@Test
	public void verify_no_null_pointer_on_setup() {
		TestExecutionSummary result = runClasses(NullPointerOnSetupSpec.class);

		assertThat(result.getTotalFailureCount(), equalTo(1L));
		assertThat(result.getFailures().get(0).getException().getMessage(), equalTo("Cannot invoke method first() on null object"));
	}
}
