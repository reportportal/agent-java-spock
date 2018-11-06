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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.util.Collections.synchronizedMap;
import static org.spockframework.runtime.model.BlockKind.WHERE;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import org.spockframework.runtime.model.*;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * Utility class, which provides static convenience methods for the operations on
 * {@link org.spockframework.runtime.model.NodeInfo} and its derivatives
 *
 * @author Dzmitry Mikhievich
 */
final class NodeInfoUtils {

	@VisibleForTesting
	static final String INHERITED_FIXTURE_NAME_TEMPLATE = "%s:%s";
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	private static final String BLOCK_SPLITTER = ": ";
	private static final String CONJUNCTION_KEYWORD = "And";

	private static final Map<BlockKind, String> BLOCK_NAMES = synchronizedMap(Maps.<BlockKind, String> newEnumMap(BlockKind.class));

	private static final Predicate<BlockInfo> SKIP_BLOCK_CONDITION = new Predicate<BlockInfo>() {
		@Override
		public boolean apply(@Nullable BlockInfo info) {
			if (info != null) {
				boolean isWhereBlock = WHERE.equals(info.getKind());
				if (isWhereBlock) {
					return Iterables.all(info.getTexts(), new Predicate<String>() {
						@Override
						public boolean apply(@Nullable String input) {
							return isNullOrEmpty(input);
						}
					});
				}
			}
			return false;
		}
	};

	private NodeInfoUtils() {
	}

	/**
	 * Create textual description for the provided feature info.
	 * Description includes formatted blocks excluding ones which carry no semantic load.
	 * <p><i>Please, be advised that generated description can be differ from the original blocks representation in the code
	 * due specifics of the Spock model.</i>
	 *
	 * @param featureInfo target feature info
	 * @return description
     */
	static String buildFeatureDescription(FeatureInfo featureInfo) {
		StringBuilder description = new StringBuilder();
		Iterator<BlockInfo> blocksIterator = featureInfo.getBlocks().iterator();
		while (blocksIterator.hasNext()) {
			BlockInfo block = blocksIterator.next();
			boolean isLast = !blocksIterator.hasNext();
			if (!SKIP_BLOCK_CONDITION.apply(block)) {
				appendBlockInfo(description, block);
				if (!isLast) {
					description.append(LINE_SEPARATOR);
				}
			} else if(isLast && description.length() > 0) {
				int lastLineSeparatorIndex = description.lastIndexOf(LINE_SEPARATOR);
				description.delete(lastLineSeparatorIndex, description.length());
			}
		}
		return description.toString();
	}

	static String buildIterationDescription(IterationInfo iterationInfo) {
		String featureDescription = buildFeatureDescription(iterationInfo.getFeature());
		return unrollIterationDescription(iterationInfo, featureDescription);
	}

	/**
	 * Get display name of the fixture. If fixture is inherited, display name is started from the source specification name.
	 *
	 * @param methodInfo method info of fixture
	 * @param inherited indicates if fixture is inherited
     * @return display name
     */
	static String getFixtureDisplayName(MethodInfo methodInfo, boolean inherited) {
		if(methodInfo != null) {
			String fixtureName = methodInfo.getName();
			if (inherited) {
				String sourceSpecName = methodInfo.getParent().getReflection().getSimpleName();
				return format(INHERITED_FIXTURE_NAME_TEMPLATE, sourceSpecName, fixtureName);
			}
			return fixtureName;
		}
		return "";
	}

	/**
	 * Get unique specification identifier.
	 *
	 * @param specInfo target spec
	 * @return identifier. If null {@code specInfo} is passed, empty identifier will be returned.
     */
	static String getSpecIdentifier(SpecInfo specInfo) {
		return specInfo != null ? specInfo.getReflection().getName() : "";
	}


	private static String unrollIterationDescription(IterationInfo iterationInfo, String iterationDescription) {
		List<String> parameterNames = iterationInfo.getFeature().getParameterNames();
		Object[] dataValues = iterationInfo.getDataValues();

		for (int i = 0; i < parameterNames.size(); i++) {
			Object value = dataValues[i];
			if (value instanceof String) {
				iterationDescription = iterationDescription
						.replaceAll("\\{?#" + parameterNames.get(i) + "\\}?", (String) value);
			}
		}

		return iterationDescription;
	}

	private static void appendBlockInfo(StringBuilder featureDescription, BlockInfo block) {
		featureDescription.append(formatBlockKind(block.getKind())).append(BLOCK_SPLITTER);
		Iterator<String> textsIterator = block.getTexts().iterator();
		// append heading block
		if (textsIterator.hasNext()) {
			featureDescription.append(textsIterator.next());
		}
		// append conjunction blocks
		while (textsIterator.hasNext()) {
			featureDescription.append(LINE_SEPARATOR).append(CONJUNCTION_KEYWORD).append(BLOCK_SPLITTER).append(textsIterator.next());
		}
	}

	/**
	 * Get formatted block kind representation.
	 *
	 * @param blockKind kind
	 * @return capitalized block kind name
     */
	private static String formatBlockKind(BlockKind blockKind) {
		if (BLOCK_NAMES.containsKey(blockKind)) {
			return BLOCK_NAMES.get(blockKind);
		} else {
			char[] initialChars = blockKind.name().toCharArray();
			char[] buffer = new char[initialChars.length];
			buffer[0] = initialChars[0];
			// iterate over characters excluding the first one
			for (int i = 1; i < initialChars.length; i++) {
				char ch = initialChars[i];
				buffer[i] = Character.toLowerCase(ch);
			}
			String blockName = new String(buffer);
			BLOCK_NAMES.put(blockKind, blockName);
			return blockName;
		}
	}
}
