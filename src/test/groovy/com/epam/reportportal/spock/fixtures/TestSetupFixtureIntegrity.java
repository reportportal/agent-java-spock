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
import com.epam.reportportal.spock.features.fixtures.SetupFixture;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;
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

public class TestSetupFixtureIntegrity {
	private final String classId = CommonUtils.namedId("class_");
	private final List<String> methodIds = Stream.generate(() -> CommonUtils.namedId("method_")).limit(2).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, null, classId, methodIds);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_setup_fixture_correct_reporting() {
		Result result = runClasses(SetupFixture.class);

		assertThat(result.getFailureCount(), equalTo(0));

		verify(client).startLaunch(any());
		verify(client).startTestItem(any(StartTestItemRQ.class));
		ArgumentCaptor<StartTestItemRQ> startCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(classId), startCaptor.capture());

		List<StartTestItemRQ> startItems = startCaptor.getAllValues();
		List<String> stepTypes = startItems.stream().map(StartTestItemRQ::getType).collect(Collectors.toList());
		assertThat(stepTypes, containsInAnyOrder(ItemType.STEP.name(), ItemType.BEFORE_METHOD.name()));

		ArgumentCaptor<FinishTestItemRQ> finishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(eq(methodIds.get(0)), finishCaptor.capture());
		verify(client).finishTestItem(eq(methodIds.get(1)), finishCaptor.capture());

		List<FinishTestItemRQ> finishItems = finishCaptor.getAllValues();
		finishItems.forEach(i-> {
			assertThat(i.getEndTime(), notNullValue());
			assertThat(i.getStatus(), equalTo(ItemStatus.PASSED.name()));
		});

		verify(client).finishTestItem(eq(classId), any());
		verifyNoMoreInteractions(client);
	}
}
