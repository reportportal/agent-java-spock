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

import static com.epam.reportportal.spock.NodeInfoUtils.INHERITED_FIXTURE_NAME_TEMPLATE;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.spockframework.runtime.model.BlockKind.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spockframework.runtime.model.*;
import com.google.common.collect.Lists;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.spockframework.util.Nullable;


/**
 * @author Dzmitry Mikhievich
 */
@RunWith(JUnitParamsRunner.class)
public class NodeInfoUtilsTest {

    @Test
    @TestCaseName("{method}:{0}")
    @Parameters(method = "featureDescription_multipleTextsInBlock," +
            "featureDescription_singleTextInBlock," +
            "featureDescription_whereBlockIgnoring," +
            "featureDescription_noBlocks" )
    public void buildFeatureDescription(String testDescription, FeatureInfo featureInfo, String expectedResult) {
        String actualDescription = NodeInfoUtils.buildFeatureDescription(featureInfo);

        assertThat(testDescription, actualDescription, equalTo(expectedResult));
    }

    private Object featureDescription_whereBlockIgnoring() {
        int expectedTextBlocksCount = 1;
        Collection<String> generatedTexts = generateBlockTexts(expectedTextBlocksCount);
        Iterator<String> generatedTextsIterator = generatedTexts.iterator();

        List<BlockInfo> blocks = asList(createBlockInfo(EXPECT, singletonList(generatedTextsIterator.next())),
                createBlockInfo(WHERE, singletonList((String) null)), createBlockInfo(WHERE, singletonList("")));

        String expectedDescription = String.format("Expect: %s", generatedTexts.toArray());

        return new Object[] { "ignoring empty 'where' blocks", createFeatureInfo(blocks), expectedDescription };
    }

    private Object featureDescription_noBlocks() {
        return new Object[] { "no blocks", createFeatureInfo(null), "" };
    }

    private Object featureDescription_singleTextInBlock() {
        int expectedTextBlocksCount = 6;
        Collection<String> generatedTexts = generateBlockTexts(expectedTextBlocksCount);
        Iterator<String> generatedTextsIterator = generatedTexts.iterator();

        List<BlockInfo> blocks = asList(createBlockInfo(SETUP, singletonList(generatedTextsIterator.next())),
                createBlockInfo(WHEN, singletonList(generatedTextsIterator.next())),
                createBlockInfo(THEN, singletonList(generatedTextsIterator.next())),
                createBlockInfo(EXPECT, singletonList(generatedTextsIterator.next())),
                createBlockInfo(CLEANUP, singletonList(generatedTextsIterator.next())),
                createBlockInfo(WHERE, singletonList(generatedTextsIterator.next())));

        String expectedDescription = String.format("Setup: %s%nWhen: %s%nThen: %s%nExpect: %s%nCleanup: %s%nWhere: %s",
                generatedTexts.toArray());

        return new Object[] { "single text in block", createFeatureInfo(blocks), expectedDescription };
    }

    private Object featureDescription_multipleTextsInBlock() {
        int expectedTextBlocksCount = 2;
        Collection<String> generatedTexts = generateBlockTexts(expectedTextBlocksCount);
        Iterator<String> generatedTextsIterator = generatedTexts.iterator();
        List<BlockInfo> blocks = singletonList(createBlockInfo(THEN, asList(generatedTextsIterator.next(), generatedTextsIterator.next())));

        String expectedDescription = String.format("Then: %s%nAnd: %s", generatedTexts.toArray());

        return new Object[] { "several texts in the single block", createFeatureInfo(blocks), expectedDescription };
    }

    @Test
    @TestCaseName("{method}(inherited={1})")
    @Parameters(method = "parametersForFixtureDescription")
    public void getFixtureDisplayName(MethodInfo methodInfo, boolean inherited, String expectedDisplayName) {
        String actualName = NodeInfoUtils.getFixtureDisplayName(methodInfo, inherited);

        assertThat(actualName, equalTo(expectedDisplayName));
    }

    @SuppressWarnings("unchecked")
    private Object parametersForFixtureDescription() {
        MethodInfo inheritedFixtureInfo = mock(MethodInfo.class, RETURNS_DEEP_STUBS);
        String inheritedFixtureName = "inherited";
        when(inheritedFixtureInfo.getName()).thenReturn(inheritedFixtureName);
        when(inheritedFixtureInfo.getParent().getReflection()).thenReturn(((Class) FixtureSource.class));
        String expectedInheritedName = format(INHERITED_FIXTURE_NAME_TEMPLATE, FixtureSource.class.getSimpleName(), inheritedFixtureName);

        MethodInfo plainFixtureInfo = mock(MethodInfo.class);
        String plainFixtureName = "inherited";
        when(plainFixtureInfo.getName()).thenReturn(plainFixtureName);

        return new Object[][] { { plainFixtureInfo, false, plainFixtureName }, { inheritedFixtureInfo, true, expectedInheritedName } };
    }

    @Test
    public void getFixtureDisplayName_fixtureIsNull() {
        String identifier = NodeInfoUtils.getFixtureDisplayName(null, true);

        assertThat(identifier, equalTo(""));
    }

    @Test
    public void getSpecIdentifier_specInfoIsNotNull() {
        Class<?> bearingClass = getClass();
        SpecInfo specInfoMock = mock(SpecInfo.class);
        when((Class) specInfoMock.getReflection()).thenReturn(bearingClass);

        String identifier = NodeInfoUtils.getSpecIdentifier(specInfoMock);
        assertThat(identifier, equalTo(bearingClass.getName()));
    }

    @Test
    public void getSpecIdentifier_specInfoIsNull() {
        String identifier = NodeInfoUtils.getSpecIdentifier(null);

        assertThat(identifier, equalTo(""));
    }

    private static final class FixtureSource {
    }

    private static Collection<String> generateBlockTexts(int count) {
        Collection<String> texts = Lists.newArrayListWithCapacity(count);
        for (int i = 0; i < count; i++) {
            texts.add(RandomStringUtils.randomAlphabetic(40));
        }
        return texts;
    }

    private static FeatureInfo createFeatureInfo(@Nullable Collection<BlockInfo> blocks) {
        FeatureInfo featureInfo = new FeatureInfo();
        if (blocks != null) {
            featureInfo.getBlocks().addAll(blocks);
        }
        return featureInfo;
    }

    private static BlockInfo createBlockInfo(BlockKind kind, List<String> texts) {
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setKind(kind);
        blockInfo.setTexts(texts);
        return blockInfo;
    }

}