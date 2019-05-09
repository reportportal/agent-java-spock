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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.model.SpecInfo;


/**
 * Implementation of {@link org.spockframework.runtime.extension.IGlobalExtension}, which provides the
 * integration with Report Portal.
 *
 * @author Dzmitry Mikhievich
 */
public class ReportPortalSpockExtension implements IGlobalExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalSpockExtension.class);

    private final BaseSpockListener reportingRunListener = new ReportPortalSpockListener();
    private final ISpockService spockService = reportingRunListener.getSpockService();

    @Override
    public void start() {
        LOGGER.info("\"LAUNCHING\" the test run");
        spockService.startLaunch();
    }

    @Override
    public void visitSpec(SpecInfo spec)
    {
        LOGGER.info("Visiting spec: " + spec.getName());
        spec.addListener(reportingRunListener);
    }

    @Override
    public void stop()
    {
        LOGGER.info("Finishing the test run \"LAUNCH\"");
        spockService.finishLaunch();
    }
}
