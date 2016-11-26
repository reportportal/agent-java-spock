package com.epam.reportportal.spock;

import static com.epam.reportportal.spock.ReportableItemFootprint.IS_NOT_PUBLISHED;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

/**
 * Created by Dzmitry_Mikhievich
 */
public class ReportableItemFootprintTest {

	@Test
	public void isNotPublishedCondition_publishedItemIsPassed() {
		ReportableItemFootprint itemMock = mock(ReportableItemFootprint.class);
		when(itemMock.isPublished()).thenReturn(true);

		boolean actualResult = IS_NOT_PUBLISHED.apply(itemMock);
		assertThat(actualResult, is(false));
	}

	@Test
	public void isNotPublishedCondition_notPublishedItemIsPassed() {
		ReportableItemFootprint itemMock = mock(ReportableItemFootprint.class);
		when(itemMock.isPublished()).thenReturn(false);

		boolean actualResult = IS_NOT_PUBLISHED.apply(itemMock);
		assertThat(actualResult, is(true));
	}

    @Test
    public void isNotPublishedCondition_nullItemIsPassed() {
        boolean actualResult = IS_NOT_PUBLISHED.apply(null);
        assertThat(actualResult, is(false));
    }
}