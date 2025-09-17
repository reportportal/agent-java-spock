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

package com.epam.reportportal.spock.inheritance;

import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.spock.ReportPortalSpockListener;
import com.epam.reportportal.spock.features.HelloSpockSpecInherited;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.spock.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SimpleInheritedTest {

	private final String classId = CommonUtils.namedId("class_");
	private final String inheritedClassId = CommonUtils.namedId("inheritedClass_");
	private final List<String> classesIds = Arrays.asList(classId, inheritedClassId);
	private final String methodId = CommonUtils.namedId("method_");
	private final List<Pair<String, Set<String>>> steps = classesIds.stream()
			.map(c -> Pair.of(c, Collections.singleton(methodId)))
			.collect(Collectors.toList());
	private final List<String> nestedSteps = Stream.generate(() -> CommonUtils.namedId("method_")).limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedStepsLink = nestedSteps.stream()
			.map(s -> Pair.of(methodId, s))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		TestUtils.mockLaunch(client, null, steps);
		TestUtils.mockNestedSteps(client, nestedStepsLink);
		TestUtils.mockBatchLogging(client);
		TestExtension.listener = new ReportPortalSpockListener(ReportPortal.create(client, standardParameters(), testExecutor()));
	}

	@Test
	public void verify_inherited_class_has_all_parent_steps() {
		TestExecutionSummary result = runClasses(HelloSpockSpecUnroll.class, HelloSpockSpecInherited.class);

		assertThat(result.getTotalFailureCount(), equalTo(0L));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		ArgumentCaptor<String> captorParent = ArgumentCaptor.forClass(String.class);
		verify(client, VerificationModeFactory.times(2)).startTestItem(captor.capture());
		verify(client, VerificationModeFactory.times(7)).startTestItem(captorParent.capture(), captor.capture());

		List<String> parents = captorParent.getAllValues();
		assertThat(parents.subList(0, 3), Every.everyItem(equalTo(classId)));
		assertThat(parents.subList(3, 7), Every.everyItem(equalTo(inheritedClassId)));

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(9));

		StartTestItemRQ parentClassRq = items.get(0);
		StartTestItemRQ inheritedClassRq = items.get(1);
		List<StartTestItemRQ> testRq = items.subList(2, 8);
		StartTestItemRQ inheritedTestRq = items.get(8);

		assertThat(parentClassRq.getCodeRef(), allOf(notNullValue(), equalTo(HelloSpockSpecUnroll.class.getCanonicalName())));
		assertThat(parentClassRq.getType(), allOf(notNullValue(), equalTo(ItemType.TEST.name())));
		assertThat(inheritedClassRq.getCodeRef(), allOf(notNullValue(), equalTo(HelloSpockSpecInherited.class.getCanonicalName())));
		assertThat(inheritedClassRq.getType(), allOf(notNullValue(), equalTo(ItemType.TEST.name())));

		assertThat(
				testRq.stream().map(StartTestItemRQ::getCodeRef).collect(Collectors.toList()),
				Every.everyItem(allOf(
						notNullValue(),
						equalTo(HelloSpockSpecUnroll.class.getCanonicalName() + "." + HelloSpockSpecUnroll.TEST_NAME)
				))
		);
		assertThat(
				testRq.stream().map(StartTestItemRQ::getType).collect(Collectors.toList()),
				Every.everyItem(equalTo(ItemType.STEP.name()))
		);
		assertThat(
				inheritedTestRq.getCodeRef(), allOf(
						notNullValue(),
						equalTo(HelloSpockSpecInherited.class.getCanonicalName() + "." + HelloSpockSpecInherited.INHERITED_TEST_NAME)
				)
		);
		assertThat(inheritedTestRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
	}
}
