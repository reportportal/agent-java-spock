/*
 * Copyright 2022 EPAM Systems
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

package com.epam.reportportal.spock.attributes;

import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.HelloSpockSpec;
import com.epam.reportportal.spock.features.attributes.SpecAttributes;
import com.epam.reportportal.spock.utils.TestExtension;
import com.epam.reportportal.spock.utils.TestUtils;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributeResource;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.Result;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SystemAttributeTest {
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
	public void verify_system_attribute_reporting() {
		runClasses(HelloSpockSpec.class);

		ArgumentCaptor<StartLaunchRQ> captor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(client).startLaunch(captor.capture());

		StartLaunchRQ startRq = captor.getValue();
		Set<ItemAttributesRQ> attributes = startRq.getAttributes();
		assertThat(attributes, allOf(notNullValue(), hasSize(greaterThanOrEqualTo(4))));
		List<ItemAttributesRQ> systemAttributes = attributes.stream().filter(ItemAttributesRQ::isSystem).collect(Collectors.toList());
		assertThat(systemAttributes, hasSize(4));
		List<String> systemAttributesKeys = systemAttributes.stream().map(ItemAttributeResource::getKey).collect(Collectors.toList());
		assertThat(systemAttributesKeys, hasItems("agent", "skippedIssue", "jvm", "os"));
		List<ItemAttributesRQ> agentName = systemAttributes.stream().filter(a -> "agent".equals(a.getKey())).collect(Collectors.toList());
		assertThat(agentName, hasSize(1));
		assertThat(agentName.get(0).getValue(), equalTo("test-name|test-version"));
	}
}
