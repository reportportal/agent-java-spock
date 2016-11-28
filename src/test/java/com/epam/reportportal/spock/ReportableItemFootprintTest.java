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