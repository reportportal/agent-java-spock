/*
 * Copyright (C) 2019 EPAM Systems
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

import com.epam.reportportal.exception.ReportPortalException;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.ta.reportportal.ws.model.ErrorRS;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.reactivex.Maybe;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.spockframework.runtime.model.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.epam.reportportal.listeners.ItemStatus.PASSED;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;
import static org.spockframework.runtime.model.MethodKind.CLEANUP_SPEC;
import static org.spockframework.runtime.model.MethodKind.SETUP;

/**
 * @author Dzmitry Mikhievich
 */
public class SpockServiceTest {
	@Mock
	private Launch launch;

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private AbstractLaunchContext launchContextMock;

	@Mock
	private SpockService spockService;

	@BeforeEach
	public void preconditions() {
		MockitoAnnotations.initMocks(this);

		launchContextMock = mock(AbstractLaunchContext.class, Answers.RETURNS_DEEP_STUBS);

		spockService = new SpockService(new MemoizingSupplier<>(() -> launch), launchContextMock);
	}

	@Test
	public void constructor_launchContextIsNull() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> new SpockService(null));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void reportIterationStart() {
		SpecInfo sourceSpecMock = mock(SpecInfo.class);
		Maybe<String> sourceSpecItemId = Maybe.just("Source spec");
		NodeFootprint sourceSpecFootprint = createNodeFootprintMock(sourceSpecItemId, null);
		when(launchContextMock.findSpecFootprint(sourceSpecMock)).thenReturn(sourceSpecFootprint);
		ArgumentCaptor<StartTestItemRQ> requestCaptor = forClass(StartTestItemRQ.class);
		when(launch.startTestItem(ArgumentMatchers.any(Maybe.class), requestCaptor.capture())).thenReturn(mock(Maybe.class));
		IterationInfo iterationMock = createIterationInfoMock(sourceSpecMock);
		when(launchContextMock.getLaunchId()).thenReturn(Maybe.just("Test Launch ID"));

		spockService.reportIterationStart(iterationMock);

		verify(launch, times(1)).startTestItem(eq(sourceSpecItemId), ArgumentMatchers.any(StartTestItemRQ.class));
		assertThat(requestCaptor.getValue().getType(), equalTo("TEST"));
	}

	//NOTE: RestEndpointIOException is not being thrown
	//	@Test
	//	public void reportIterationStart_rpPostThrowsException() throws RestEndpointIOException {
	//		SpockService spockServiceSpy = spy(spockService);
	//		RestEndpointIOException rpException = new RestEndpointIOException("");
	//		when(launch.startTestItem(isNull(Maybe.class), any(StartTestItemRQ.class))).thenThrow(rpException);
	//        IterationInfo iterationMock = createIterationInfoMock(mock(SpecInfo.class));
	//        when(launchContextMock.getLaunchId()).thenReturn(Maybe.just("Test Launch ID"));
	//
	//		spockServiceSpy.reportIterationStart(iterationMock);
	//
	//		verify(spockServiceSpy, times(1)).handleRpException(eq(rpException), anyString());
	//	}

	// NOTE: finishTestItem no longer returns a value
	@Test
	public void reportTestItemFinish() {
		ArgumentCaptor<FinishTestItemRQ> requestCaptor = forClass(FinishTestItemRQ.class);
		//        when(launch.finishTestItem(any(Maybe.class), requestCaptor.capture())).thenReturn(null);
		Maybe<String> itemId = Maybe.just("Test item ID");
		ReportableItemFootprint footprintMock = createGenericFootprintMock(itemId, ItemStatus.INFO);

		spockService.reportTestItemFinish(footprintMock);

		verify(launch, times(1)).finishTestItem(eq(itemId), ArgumentMatchers.any(FinishTestItemRQ.class));
		//		assertThat(requestCaptor.getValue().getStatus(), equalTo(status));
		verify(footprintMock, times(1)).markAsPublished();
	}

	//    @Test
	//    public void reportTestItemFinish_rpPostThrowsException() {
	//        SpockService spockServiceSpy = spy(spockService);
	//        RestEndpointIOException rpException = new RestEndpointIOException("");
	//        doThrow(rpException).when(launch).finishTestItem(any(Maybe.class), any(FinishTestItemRQ.class));
	//        Maybe<String> itemId = Maybe.just("Another item ID");
	//        ReportableItemFootprint footprintMock = createGenericFootprintMock(itemId, ItemStatus.INFO);
	//
	//        spockServiceSpy.reportTestItemFinish(footprintMock);
	//
	//        verify(spockServiceSpy, times(1)).handleRpException(eq(rpException), anyString());
	//        verify(footprintMock, times(1)).markAsPublished();
	//    }

	// NOTE: No longer valid - rpService.log() is not exposed in the reportTestItemFailure method
	//	@Test
	//	public void reportTestItemFailure() throws RestEndpointIOException {
	//		String itemId = "Test item ID";
	//		ArgumentCaptor<SaveLogRQ> requestCaptor = forClass(SaveLogRQ.class);
	//		when(rpServiceMock.log(requestCaptor.capture())).thenReturn(null);
	//		//ReportableItemFootprint footprintMock = createGenericFootprintMock(itemId, null);
	//
	//		//spockService.reportTestItemFailure(footprintMock, createErrorInfoMock(new Exception()));
	//        spockService.reportTestItemFailure(createErrorInfoMock(new Exception()));
	//
	//		verify(rpServiceMock, times(1)).log(any(SaveLogRQ.class));
	//		verifyErrorLogRQ(requestCaptor.getValue(), itemId);
	//	}

	// NOTE: No longer valid - rpService.log() is not exposed in the reportTestItemFailure method
	//	@Test
	//	public void reportTestItemFailure_rpPostThrowsException() throws RestEndpointIOException {
	//		SpockService spockServiceSpy = spy(spockService);
	//		RestEndpointIOException rpException = new RestEndpointIOException("");
	//		when(rpServiceMock.log(any(SaveLogRQ.class))).thenThrow(rpException);
	//		//ReportableItemFootprint footprintMock = createGenericFootprintMock("Test item ID", null);
	//
	//		//spockServiceSpy.reportTestItemFailure(footprintMock, createErrorInfoMock(new Exception()));
	//        spockServiceSpy.reportTestItemFailure(createErrorInfoMock(new Exception()));
	//
	//		verify(spockServiceSpy, times(1)).handleRpException(eq(rpException), anyString());
	//	}

	@Test
	public void handleRpException() throws IllegalAccessException {
		spockService.handleRpException(new ReportPortalException(500, "", new ErrorRS()), "");

		AtomicBoolean rpIsDown = (AtomicBoolean) readField(spockService, "rpIsDown", true);
		assertThat(rpIsDown.get(), is(true));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findFixtureOwner_specScopedFixture() {
		SpecInfo sourceSpecMock = mock(SpecInfo.class);
		NodeFootprint ownerFootprintMock = mock(NodeFootprint.class);
		when(launchContextMock.findSpecFootprint(sourceSpecMock)).thenReturn(ownerFootprintMock);
		MethodInfo fixtureMock = createMethodInfoMock(sourceSpecMock, CLEANUP_SPEC);

		NodeFootprint actualFootprint = spockService.findFixtureOwner(fixtureMock);

		assertThat(actualFootprint, equalTo(ownerFootprintMock));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findFixtureOwner_featureScopedFixture() {
		SpecInfo sourceSpecMock = mock(SpecInfo.class);
		IterationInfo ownerIterationMock = mock(IterationInfo.class);
		NodeFootprint ownerFootprintMock = mock(NodeFootprint.class);
		when(launchContextMock.getRuntimePointerForSpec(sourceSpecMock).getCurrentIteration()).thenReturn(ownerIterationMock);
		when(launchContextMock.findIterationFootprint(ownerIterationMock)).thenReturn(ownerFootprintMock);
		MethodInfo fixtureMock = createMethodInfoMock(sourceSpecMock, SETUP);

		NodeFootprint actualFootprint = spockService.findFixtureOwner(fixtureMock);

		assertThat(actualFootprint, equalTo(ownerFootprintMock));
	}

	@Test
	public void calculateFootprintStatus_footprintHasStatus() {
		ItemStatus expectedStatus = PASSED;
		ReportableItemFootprint<?> footprint = mock(ReportableItemFootprint.class);
		when(footprint.getStatus()).thenReturn(Optional.of(expectedStatus));

		ItemStatus actualStatus = SpockService.calculateFootprintStatus(footprint);

		assertThat(actualStatus, equalTo(expectedStatus));
	}

	@Test
	public void calculateFootprintStatus_footprintHasNoStatusAndHasDescendants() {
		ReportableItemFootprint<?> footprint = mock(ReportableItemFootprint.class);
		when(footprint.hasDescendants()).thenReturn(true);
		when(footprint.getStatus()).thenReturn(Optional.empty());

		ItemStatus actualStatus = SpockService.calculateFootprintStatus(footprint);

		assertThat(actualStatus, equalTo(ItemStatus.WARN));
	}

	@Test
	public void calculateFootprintStatus_footprintHasNoStatusAndHasNoDescendants() {
		ReportableItemFootprint<?> footprint = mock(ReportableItemFootprint.class);
		when(footprint.hasDescendants()).thenReturn(false);
		when(footprint.getStatus()).thenReturn(Optional.empty());

		ItemStatus actualStatus = SpockService.calculateFootprintStatus(footprint);
		// Passed by default
		assertThat(actualStatus, is(PASSED));
	}

	@Test
	public void isMonolithicParametrizedFeature_notParametrizedFeature() {
		FeatureInfo feature = mock(FeatureInfo.class);
		when(feature.isParameterized()).thenReturn(false);

		boolean result = SpockService.isMonolithicParametrizedFeature(feature);

		assertThat(result, is(false));
	}

	@Test
	public void isMonolithicParametrizedFeature_parametrizedNotUnrolledFeature() {
		FeatureInfo feature = mock(FeatureInfo.class);
		when(feature.isParameterized()).thenReturn(true);
		when(feature.isReportIterations()).thenReturn(false);

		boolean result = SpockService.isMonolithicParametrizedFeature(feature);

		assertThat(result, is(true));
	}

	@Test
	public void isMonolithicParametrizedFeature_parametrizedUnrolledFeature() {
		FeatureInfo feature = mock(FeatureInfo.class);
		when(feature.isParameterized()).thenReturn(true);
		when(feature.isReportIterations()).thenReturn(true);

		boolean result = SpockService.isMonolithicParametrizedFeature(feature);

		assertThat(result, is(false));
	}

	private static MethodInfo createMethodInfoMock(SpecInfo sourceSpec, MethodKind kind) {
		MethodInfo mock = mock(MethodInfo.class);
		when(mock.getKind()).thenReturn(kind);
		when(mock.getParent()).thenReturn(sourceSpec);
		return mock;
	}

	private static ReportableItemFootprint createGenericFootprintMock(Maybe<String> itemId, ItemStatus status) {
		ReportableItemFootprint mock = mock(ReportableItemFootprint.class);
		when(mock.getId()).thenReturn(itemId);
		when(mock.getStatus()).thenReturn(Optional.ofNullable(status));
		return mock;
	}

	private static NodeFootprint createNodeFootprintMock(Maybe<String> itemId, ItemStatus status) {
		NodeFootprint mock = mock(NodeFootprint.class);
		when(mock.getId()).thenReturn(itemId);
		when(mock.getStatus()).thenReturn(Optional.ofNullable(status));
		return mock;
	}

	private static ErrorInfo createErrorInfoMock(Throwable exception) {
		ErrorInfo errorInfoMock = mock(ErrorInfo.class);
		when(errorInfoMock.getException()).thenReturn(exception);
		return errorInfoMock;
	}

	private static IterationInfo createIterationInfoMock(SpecInfo sourceSpec) {
		IterationInfo iterationInfoMock = mock(IterationInfo.class);
		FeatureInfo featureInfo = new FeatureInfo();
		featureInfo.setParent(sourceSpec);

		when(iterationInfoMock.getFeature()).thenReturn(featureInfo);
		return iterationInfoMock;
	}

	private static void verifyErrorLogRQ(SaveLogRQ errorLogRQ, String originItemId) {
		assertThat(errorLogRQ.getLevel(), equalTo("ERROR"));
		assertThat(errorLogRQ.getMessage(), not(Matchers.emptyString()));
	}

}