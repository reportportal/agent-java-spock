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

package com.epam.reportportal.spock.coderef;

import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.HelloSpockSpecUnroll;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.core.Every;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.VerificationModeFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SimpleCodeRefTest {

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
	public void verify_static_test_code_reference_generation() {
		TestExecutionSummary result = runClasses(HelloSpockSpecUnroll.class);

		assertThat(result.getTotalFailureCount(), equalTo(0L));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(captor.capture());
		verify(client, VerificationModeFactory.times(3)).startTestItem(same(classId), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(4));

		StartTestItemRQ classRq = items.get(0);
		List<StartTestItemRQ> testRq = items.subList(1, 4);

		assertThat(classRq.getCodeRef(), allOf(notNullValue(), equalTo(HelloSpockSpecUnroll.class.getCanonicalName())));
		assertThat(classRq.getType(), allOf(notNullValue(), equalTo(ItemType.TEST.name())));
		assertThat(
				testRq.stream().map(StartTestItemRQ::getCodeRef).collect(Collectors.toList()),
				Every.everyItem(allOf(notNullValue(), equalTo(HelloSpockSpecUnroll.class.getCanonicalName() + "." + HelloSpockSpecUnroll.TEST_NAME)))
		);
		assertThat(testRq.stream().map(StartTestItemRQ::getType).collect(Collectors.toList()), Every.everyItem(equalTo(ItemType.STEP.name())));
	}
}
