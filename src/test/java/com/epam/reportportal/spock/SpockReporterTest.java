/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/agent-java-spock
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.spock;

import static com.epam.reportportal.listeners.Statuses.PASSED;
import static org.apache.commons.lang3.reflect.FieldUtils.readField;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.spockframework.runtime.model.MethodKind.CLEANUP_SPEC;
import static org.spockframework.runtime.model.MethodKind.SETUP;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.spockframework.runtime.model.*;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.restclient.endpoint.exception.RestEndpointIOException;
import com.epam.reportportal.service.IReportPortalService;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.google.common.base.Optional;

/**
 * @author Dzmitry Mikhievich
 */
@RunWith(MockitoJUnitRunner.class)
public class SpockReporterTest {
	@Mock
	private IReportPortalService rpServiceMock;
	@Mock
	private ListenerParameters listenerParametersMock;
	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private AbstractLaunchContext launchContextMock;
	@InjectMocks
	private SpockReporter spockReporter;

	@Test(expected = IllegalArgumentException.class)
	public void constructor_rpServiceIsNull() {
		new SpockReporter(null, listenerParametersMock, launchContextMock);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_parametersArgIsNull() {
		new SpockReporter(rpServiceMock, null, launchContextMock);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructor_launchContextIsNull() {
		new SpockReporter(rpServiceMock, listenerParametersMock, null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void reportIterationStart() throws RestEndpointIOException {
		SpecInfo sourceSpecMock = mock(SpecInfo.class);
		String sourceSpecItemId = "Source spec";
		NodeFootprint sourceSpecFootprint = createNodeFootprintMock(sourceSpecItemId, null);
		when(launchContextMock.findSpecFootprint(sourceSpecMock)).thenReturn(sourceSpecFootprint);
		ArgumentCaptor<StartTestItemRQ> requestCaptor = forClass(StartTestItemRQ.class);
		when(rpServiceMock.startTestItem(anyString(), requestCaptor.capture())).thenReturn(mock(EntryCreatedRS.class));
		IterationInfo iterationMock = createIterationInfoMock(sourceSpecMock);

		spockReporter.reportIterationStart(iterationMock);

		verify(rpServiceMock, times(1)).startTestItem(eq(sourceSpecItemId), any(StartTestItemRQ.class));
		assertThat(requestCaptor.getValue().getType(), equalTo("TEST"));
	}

	@Test
	public void reportIterationStart_rpPostThrowsException() throws RestEndpointIOException {
		SpockReporter spockReporterSpy = spy(spockReporter);
		RestEndpointIOException rpException = new RestEndpointIOException("");
		when(rpServiceMock.startTestItem(isNull(String.class), any(StartTestItemRQ.class))).thenThrow(rpException);
		IterationInfo iterationMock = createIterationInfoMock(mock(SpecInfo.class));

		spockReporterSpy.reportIterationStart(iterationMock);

		verify(spockReporterSpy, times(1)).handleRpException(eq(rpException), anyString());
	}

	@Test
	public void reportTestItemFinish() throws RestEndpointIOException {
		ArgumentCaptor<FinishTestItemRQ> requestCaptor = forClass(FinishTestItemRQ.class);
		when(rpServiceMock.finishTestItem(anyString(), requestCaptor.capture())).thenReturn(null);
		String itemId = "Test item ID";
		String status = "Test status";
		ReportableItemFootprint footprintMock = createGenericFootprintMock(itemId, status);

		spockReporter.reportTestItemFinish(footprintMock);

		verify(rpServiceMock, times(1)).finishTestItem(eq(itemId), any(FinishTestItemRQ.class));
		assertThat(requestCaptor.getValue().getStatus(), equalTo(status));
		verify(footprintMock, times(1)).markAsPublished();
	}

	@Test
	public void reportTestItemFinish_rpPostThrowsException() throws RestEndpointIOException {
		SpockReporter spockReporterSpy = spy(spockReporter);
		RestEndpointIOException rpException = new RestEndpointIOException("");
		when(rpServiceMock.finishTestItem(anyString(), any(FinishTestItemRQ.class))).thenThrow(rpException);
		ReportableItemFootprint footprintMock = createGenericFootprintMock("Another item ID", null);

		spockReporterSpy.reportTestItemFinish(footprintMock);

		verify(spockReporterSpy, times(1)).handleRpException(eq(rpException), anyString());
		verify(footprintMock, times(1)).markAsPublished();
	}

	@Test
	public void reportTestItemFailure() throws RestEndpointIOException {
		String itemId = "Test item ID";
		ArgumentCaptor<SaveLogRQ> requestCaptor = forClass(SaveLogRQ.class);
		when(rpServiceMock.log(requestCaptor.capture())).thenReturn(null);
		ReportableItemFootprint footprintMock = createGenericFootprintMock(itemId, null);

		spockReporter.reportTestItemFailure(footprintMock, createErrorInfoMock(new Exception()));

		verify(rpServiceMock, times(1)).log(any(SaveLogRQ.class));
		verifyErrorLogRQ(requestCaptor.getValue(), itemId);
	}

	@Test
	public void reportTestItemFailure_rpPostThrowsException() throws RestEndpointIOException {
		SpockReporter spockReporterSpy = spy(spockReporter);
		RestEndpointIOException rpException = new RestEndpointIOException("");
		when(rpServiceMock.log(any(SaveLogRQ.class))).thenThrow(rpException);
		ReportableItemFootprint footprintMock = createGenericFootprintMock("Test item ID", null);

		spockReporterSpy.reportTestItemFailure(footprintMock, createErrorInfoMock(new Exception()));

		verify(spockReporterSpy, times(1)).handleRpException(eq(rpException), anyString());
	}

	@Test
	public void handleRpException() throws IllegalAccessException {
		spockReporter.handleRpException(new RestEndpointIOException(""), "");

		AtomicBoolean rpIsDown = (AtomicBoolean) readField(spockReporter, "rpIsDown", true);
		assertThat(rpIsDown.get(), is(true));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findFixtureOwner_specScopedFixture() {
		SpecInfo sourceSpecMock = mock(SpecInfo.class);
		NodeFootprint ownerFootprintMock = mock(NodeFootprint.class);
		when(launchContextMock.findSpecFootprint(sourceSpecMock)).thenReturn(ownerFootprintMock);
		MethodInfo fixtureMock = createMethodInfoMock(sourceSpecMock, CLEANUP_SPEC);

		NodeFootprint actualFootprint = spockReporter.findFixtureOwner(fixtureMock);

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

		NodeFootprint actualFootprint = spockReporter.findFixtureOwner(fixtureMock);

		assertThat(actualFootprint, equalTo(ownerFootprintMock));
	}

	@Test
	public void calculateFootprintStatus_footprintHasStatus() {
		String expectedStatus = PASSED;
		ReportableItemFootprint<?> footprint = mock(ReportableItemFootprint.class);
		when(footprint.getStatus()).thenReturn(Optional.of(expectedStatus));

		String actualStatus = SpockReporter.calculateFootprintStatus(footprint);

		assertThat(actualStatus, equalTo(expectedStatus));
	}

	@Test
	public void calculateFootprintStatus_footprintHasNoStatusAndHasDescendants() {
		ReportableItemFootprint<?> footprint = mock(ReportableItemFootprint.class);
		when(footprint.hasDescendants()).thenReturn(true);
		when(footprint.getStatus()).thenReturn(Optional.<String> absent());

		String actualStatus = SpockReporter.calculateFootprintStatus(footprint);

		assertThat(actualStatus, nullValue(String.class));
	}

	@Test
	public void calculateFootprintStatus_footprintHasNoStatusAndHasNoDescendants() {
		ReportableItemFootprint<?> footprint = mock(ReportableItemFootprint.class);
		when(footprint.hasDescendants()).thenReturn(false);
		when(footprint.getStatus()).thenReturn(Optional.<String> absent());

		String actualStatus = SpockReporter.calculateFootprintStatus(footprint);
		// Passed by default
		assertThat(actualStatus, is(PASSED));
	}

	@Test
	public void isMonolithicParametrizedFeature_notParametrizedFeature() {
		FeatureInfo feature = mock(FeatureInfo.class);
		when(feature.isParameterized()).thenReturn(false);

		boolean result = SpockReporter.isMonolithicParametrizedFeature(feature);

		assertThat(result, is(false));
	}

	@Test
	public void isMonolithicParametrizedFeature_parametrizedNotUnrolledFeature() {
		FeatureInfo feature = mock(FeatureInfo.class);
		when(feature.isParameterized()).thenReturn(true);
		when(feature.isReportIterations()).thenReturn(false);

		boolean result = SpockReporter.isMonolithicParametrizedFeature(feature);

		assertThat(result, is(true));
	}

	@Test
	public void isMonolithicParametrizedFeature_parametrizedUnrolledFeature() {
		FeatureInfo feature = mock(FeatureInfo.class);
		when(feature.isParameterized()).thenReturn(true);
		when(feature.isReportIterations()).thenReturn(true);

		boolean result = SpockReporter.isMonolithicParametrizedFeature(feature);

		assertThat(result, is(false));
	}

	private static MethodInfo createMethodInfoMock(SpecInfo sourceSpec, MethodKind kind) {
		MethodInfo mock = mock(MethodInfo.class);
		when(mock.getKind()).thenReturn(kind);
		when(mock.getParent()).thenReturn(sourceSpec);
		return mock;
	}

	private static ReportableItemFootprint createGenericFootprintMock(String itemId, @Nullable String status) {
		ReportableItemFootprint mock = mock(ReportableItemFootprint.class);
		when(mock.getId()).thenReturn(itemId);
		when(mock.getStatus()).thenReturn(Optional.fromNullable(status));
		return mock;
	}

	private static NodeFootprint createNodeFootprintMock(String itemId, @Nullable String status) {
		NodeFootprint mock = mock(NodeFootprint.class);
		when(mock.getId()).thenReturn(itemId);
		when(mock.getStatus()).thenReturn(Optional.fromNullable(status));
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
		assertThat(errorLogRQ.getTestItemId(), equalTo(originItemId));
		assertThat(errorLogRQ.getLevel(), equalTo("ERROR"));
		assertThat(errorLogRQ.getMessage(), not(isEmptyString()));
	}
}