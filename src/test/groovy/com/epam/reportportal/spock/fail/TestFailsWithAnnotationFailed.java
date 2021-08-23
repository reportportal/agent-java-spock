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

package com.epam.reportportal.spock.fail;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.FailsWithAnnotationFail;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import okhttp3.MultipartBody;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestFailsWithAnnotationFailed {

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
	@SuppressWarnings("unchecked")
	public void verify_fail_with_failed_reporting() {
		Result result = runClasses(FailsWithAnnotationFail.class);

		assertThat(result.getFailureCount(), equalTo(1));

		verify(client).startLaunch(any());
		ArgumentCaptor<StartTestItemRQ> startCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(any(StartTestItemRQ.class));
		verify(client, times(2)).startTestItem(same(classId), startCaptor.capture());

		List<StartTestItemRQ> items = startCaptor.getAllValues();

		items.forEach(i -> {
			assertThat(i.getType(), equalTo(ItemType.STEP.name()));
			assertThat(i.isHasStats(), equalTo(Boolean.TRUE));
		});

		ArgumentCaptor<FinishTestItemRQ> finishStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		methodIds.forEach(s -> verify(client).finishTestItem(eq(s), finishStepCaptor.capture()));

		List<String> finishItemStatuses = finishStepCaptor.getAllValues()
				.stream()
				.map(FinishExecutionRQ::getStatus)
				.collect(Collectors.toList());
		assertThat(finishItemStatuses, containsInAnyOrder(ItemStatus.PASSED.name(), ItemStatus.FAILED.name()));
		verify(client).finishTestItem(eq(classId), any());

		ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeast(1)).log(logCaptor.capture());
		List<SaveLogRQ> rqs = toSaveLogRQ(logCaptor.getAllValues()).stream()
				.filter(rq -> LogLevel.ERROR.name().equals(rq.getLevel()))
				.collect(Collectors.toList());
		assertThat(rqs, hasSize(1));

		//noinspection unchecked
		verify(client, atLeast(1)).log(any(List.class));
		verifyNoMoreInteractions(client);
	}
}
