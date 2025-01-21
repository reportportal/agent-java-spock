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

package com.epam.reportportal.spock.fixtures;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.fixtures.CleanupFixture;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class TestCleanupFixtureIntegrity {
	private final String launchId = CommonUtils.namedId("launch_");
	private final String classId = CommonUtils.namedId("class_");
	private final List<String> methodIds = Stream.generate(() -> CommonUtils.namedId("method_")).limit(2).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, launchId, classId, methodIds);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_cleanup_fixture_correct_reporting() {
		TestExecutionSummary result = runClasses(CleanupFixture.class);

		assertThat(result.getTotalFailureCount(), equalTo(0L));

		verify(client).getProjectSettings();
		verify(client).startLaunch(any());
		verify(client).startTestItem(any(StartTestItemRQ.class));
		ArgumentCaptor<StartTestItemRQ> startCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(classId), startCaptor.capture());

		List<StartTestItemRQ> startItems = startCaptor.getAllValues();
		List<String> stepTypes = startItems.stream().map(StartTestItemRQ::getType).collect(Collectors.toList());
		assertThat(stepTypes, containsInAnyOrder(ItemType.STEP.name(), ItemType.AFTER_METHOD.name()));

		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		methodIds.forEach(id -> verify(client).finishTestItem(eq(id), finishCaptor.capture()));

		List<FinishTestItemRQ> finishItems = finishCaptor.getAllValues();
		finishItems.forEach(i-> {
			assertThat(i.getEndTime(), notNullValue());
			assertThat(i.getStatus(), equalTo(ItemStatus.PASSED.name()));
			assertThat(i.getIssue(), nullValue());
		});

		verify(client).finishTestItem(eq(classId), any());
		verify(client).finishLaunch(eq(launchId), any());
		verifyNoMoreInteractions(client);
	}
}
