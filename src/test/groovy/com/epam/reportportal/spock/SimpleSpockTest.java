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

package com.epam.reportportal.spock;

import com.epam.reportportal.listeners.ItemType;

import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.features.HelloSpockSpec;
import com.epam.reportportal.spock.utils.CodRefExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.runner.Result;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.Collections;
import java.util.List;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class SimpleSpockTest {

	private final String classId = CommonUtils.namedId("class_");
	private final String methodId = CommonUtils.namedId("method_");

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, null, null, classId, methodId);
		TestUtils.mockBatchLogging(client);
		SpockService service = new SpockService(ReportPortal.create(client,
				standardParameters(),
				testExecutor()
		));
		CodRefExtension.listener = new ReportPortalSpockListener(service);
	}

	@Test
	public void verify_static_test_code_reference_generation() {
		Result result = runClasses(Collections.singletonList(CodRefExtension.class), HelloSpockSpec.class);

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(ArgumentMatchers.startsWith("root_"), captor.capture());
		verify(client).startTestItem(same(classId), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(2));

		StartTestItemRQ classRq = items.get(0);
		StartTestItemRQ testRq = items.get(1);

		assertThat(classRq.getCodeRef(), allOf(notNullValue(), equalTo(HelloSpockSpec.class.getCanonicalName())));
		assertThat(classRq.getType(), allOf(notNullValue(), equalTo(ItemType.TEST.name())));
		assertThat(testRq.getCodeRef(),
				allOf(notNullValue(),
						equalTo(HelloSpockSpec.class.getCanonicalName() + "." + HelloSpockSpec.class.getDeclaredMethods()[0].getName())
				)
		);
	}
}