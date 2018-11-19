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

import com.epam.reportportal.listeners.ReportPortalListenerContext;
import org.spockframework.runtime.AbstractRunListener;

import java.util.LinkedList;

/**
 * Gateway to the {@link ReportPortalListenerContext}, which provides additional memoization facilities:
 * during the setting of the running item id this component additionally put it in the local LIFO data structure.
 * The main purpose is bypassing an issue with "mixing" of event tracking, i.e. using the current approach
 * the notification order can look like:
 * 1) beforeFeature/beforeIteration is called on {@link AbstractRunListener} - item is tracked on RP and stored in context
 * 2) 'setup' method for iteration/feature from p.1. is registered - associated item is tracked on RP and stored in context
 * (item from p.1 is erased)
 * 3) 'setup' method is done - context is flushed
 * 4) As a result all trackable logging event during the fixture/iteration execution are missed due context emptiness
 *
 * Created by Dzmitry_Mikhievich
 */
class DisorderedListenerContextDelegate {

	private static ThreadLocal<LinkedList<String>> itemsRegistry = new ThreadLocal<LinkedList<String>>();

	private DisorderedListenerContextDelegate() {
	}

	static void setRunningNowItemId(String runningStepId) {
        initializeRegistryIfNecessary();
        if(runningStepId == null) {
            LinkedList<String> registry = itemsRegistry.get();
            if(registry.size() > 1) {
                //Flush current
                registry.removeFirst();
                ReportPortalListenerContext.setRunningNowItemId(registry.pop());
            } else {
                ReportPortalListenerContext.setRunningNowItemId(null);
            }
        } else {
            itemsRegistry.get().push(runningStepId);
            ReportPortalListenerContext.setRunningNowItemId(runningStepId);
        }
    }

    private static void initializeRegistryIfNecessary() {
        if(itemsRegistry.get() == null) {
            itemsRegistry.set(new LinkedList<String>());
        }
    }

}
