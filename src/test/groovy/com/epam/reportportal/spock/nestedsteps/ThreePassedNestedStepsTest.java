/*
 * Copyright 2021 EPAM Systems
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

package com.epam.reportportal.spock.nestedsteps;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.HelloSpockSpec;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
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
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ThreePassedNestedStepsTest {

	private final String classId = CommonUtils.namedId("class_");
	private final String methodId = CommonUtils.namedId("method_");
	private final List<String> nestedSteps = Stream.generate(() -> CommonUtils.namedId("method_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedStepsLink = nestedSteps.stream()
			.map(s -> Pair.of(methodId, s))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, null, classId, methodId);
		TestUtils.mockNestedSteps(client, nestedStepsLink);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_passed_nested_step_reporting() {
		TestExecutionSummary result = runClasses(HelloSpockSpec.class);

		assertThat(result.getTotalFailureCount(), equalTo(0L));

		verify(client).getProjectSettings();
		verify(client).getApiInfo();
		ArgumentCaptor<StartTestItemRQ> startCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(any(StartTestItemRQ.class));
		verify(client).startTestItem(same(classId), any(StartTestItemRQ.class));
		verify(client, times(3)).startTestItem(same(methodId), startCaptor.capture());

		List<StartTestItemRQ> items = startCaptor.getAllValues();

		items.forEach(i -> {
			assertThat(i.getType(), equalTo(ItemType.STEP.name()));
			assertThat(i.isHasStats(), equalTo(Boolean.FALSE));
		});

		ArgumentCaptor<FinishTestItemRQ> finishNestedStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		nestedSteps.forEach(s -> verify(client).finishTestItem(eq(s), finishNestedStepCaptor.capture()));

		List<FinishTestItemRQ> finishItems = finishNestedStepCaptor.getAllValues();
		finishItems.forEach(i -> assertThat(i.getStatus(), equalTo(ItemStatus.PASSED.name())));

		ArgumentCaptor<FinishTestItemRQ> finishStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(methodId), finishStepCaptor.capture());
		assertThat(finishStepCaptor.getValue().getStatus(), equalTo(ItemStatus.PASSED.name()));
	}
}
