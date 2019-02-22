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
