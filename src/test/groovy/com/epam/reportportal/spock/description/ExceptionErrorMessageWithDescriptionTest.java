package com.epam.reportportal.spock.description;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.fail.FailsInDifferentMethod;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ExceptionErrorMessageWithDescriptionTest {

	private final String launchId = CommonUtils.namedId("launch_");
	private final String classId = CommonUtils.namedId("class_");
	private final List<String> methodIds = Stream.generate(() -> CommonUtils.namedId("method_")).limit(1).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	private static final String ERROR_DESCRIPTION_EXCEPTION = "Setup: \n\n---\n\nError:\njava.lang.IllegalStateException: Some test flow failure";

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, launchId, classId, methodIds);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_error_exception_message_with_description() {
		TestExecutionSummary result = runClasses(FailsInDifferentMethod.class);

		assertThat(result.getTotalFailureCount(), equalTo(1L));

		ArgumentCaptor<FinishTestItemRQ> finishStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);

		methodIds.forEach(s -> verify(client).finishTestItem(eq(s), finishStepCaptor.capture()));

		List<FinishTestItemRQ> failedFinishItemStatuses = finishStepCaptor.getAllValues()
				.stream()
				.filter(fis -> fis.getStatus().equals(ItemStatus.FAILED.name()))
				.collect(Collectors.toList());

		assertThat(failedFinishItemStatuses, hasSize(1));
		assertThat(failedFinishItemStatuses.iterator().next().getDescription(), startsWith(ERROR_DESCRIPTION_EXCEPTION));
	}
}