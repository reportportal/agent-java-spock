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
import static org.spockframework.runtime.model.BlockKind.WHERE;

import java.util.Iterator;

import javax.annotation.Nullable;

import org.spockframework.runtime.model.BlockInfo;
import org.spockframework.runtime.model.BlockKind;
import org.spockframework.runtime.model.FeatureInfo;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Created by Dzmitry_Mikhievich
 */
public class NodeInfoUtils {

	private static final String SPACE = " ";

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

	static String getFeatureDescription(FeatureInfo featureInfo) {
		StringBuilder description = new StringBuilder();
		Iterator<BlockInfo> blocksIterator = featureInfo.getBlocks().iterator();
		while (blocksIterator.hasNext()) {
			BlockInfo block = blocksIterator.next();
			if (!SKIP_BLOCK_CONDITION.apply(block)) {
				description.append(formatBlockKind(block.getKind())).append(SPACE);
				Joiner.on(SPACE).appendTo(description, block.getTexts());
				boolean last = blocksIterator.hasNext();
				if (last) {
					description.append(System.lineSeparator());
				}
			}
		}
		return description.toString();
	}

	private static String formatBlockKind(BlockKind blockKind) {
		char[] initialChars = blockKind.name().toCharArray();
		char[] buffer = new char[initialChars.length];
		buffer[0] = initialChars[0];
		// iterate over characters excluding the first one
		for (int i = 1; i < initialChars.length; i++) {
			char ch = initialChars[i];
			if (Character.isUpperCase(ch)) {
				ch = Character.toLowerCase(ch);
			}
			buffer[i] = ch;
		}
		return new String(buffer);
	}
}
