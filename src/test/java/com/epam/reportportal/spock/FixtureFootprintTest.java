package com.epam.reportportal.spock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.Test;

/**
 * Created by Dzmitry_Mikhievich
 */
public class FixtureFootprintTest {

    @Test
    public void hasDescendants() {
        FixtureFootprint footprint = mock(FixtureFootprint.class, CALLS_REAL_METHODS);
        assertThat(footprint.hasDescendants(), is(false));
    }
}