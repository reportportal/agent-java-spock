package com.epam.reportportal.spock.description;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.fail.HelloSpockSpecFailed;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import org.apache.commons.lang3.tuple.Pair;
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

public class ConditionNotSatisfiedErrorMessageWithoutDescriptionTest {

	private final String classId = CommonUtils.namedId("class_");
	private final String methodId = CommonUtils.namedId("method_");
	private final List<String> nestedSteps = Stream.generate(() -> CommonUtils.namedId("method_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedStepsLink = nestedSteps.stream()
			.map(s -> Pair.of(methodId, s))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	private static final String ERROR_DESCRIPTION = "Error:\nCondition not satisfied:\nname.size() == length\n|    |      |  |\n|    6      |  7\nScotty      false\n";

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, null, classId, methodId);
		TestUtils.mockNestedSteps(client, nestedStepsLink);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_error_condition_not_satisfied_message_without_description() {

		TestExecutionSummary result = runClasses(HelloSpockSpecFailed.class);

		assertThat(result.getTotalFailureCount(), equalTo(1L));

		ArgumentCaptor<FinishTestItemRQ> finishNestedStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		nestedSteps.forEach(s -> verify(client).finishTestItem(eq(s), finishNestedStepCaptor.capture()));

		List<FinishTestItemRQ> passedFinishItemStatuses = finishNestedStepCaptor.getAllValues()
				.stream()
				.filter(finishTestItemRQ -> finishTestItemRQ.getStatus().equals(ItemStatus.PASSED.name()))
				.collect(Collectors.toList());

		assertThat(passedFinishItemStatuses, hasSize(2));

		passedFinishItemStatuses.forEach(finishTestItemRQ -> assertThat(finishTestItemRQ.getDescription(), is(nullValue())));

		List<FinishTestItemRQ> failedFinishItemStatuses = finishNestedStepCaptor.getAllValues()
				.stream()
				.filter(finishTestItemRQ -> finishTestItemRQ.getStatus().equals(ItemStatus.FAILED.name()))
				.collect(Collectors.toList());

		assertThat(failedFinishItemStatuses, hasSize(1));

		assertThat(failedFinishItemStatuses.iterator().next().getDescription(), startsWith(ERROR_DESCRIPTION));
	}
}