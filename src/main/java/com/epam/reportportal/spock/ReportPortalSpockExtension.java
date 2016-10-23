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

import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.model.SpecInfo;

import com.epam.reportportal.guice.Injector;

/**
 * @author Dzmitry Mikhievich
 */
public class ReportPortalSpockExtension implements IGlobalExtension {

	private static Injector injector = Injector.getInstance().getChildInjector(new SpockListenersModule());

	private ISpockReporter spockReporter = injector.getBean(ISpockReporter.class);

	@Override
	public void start() {
		spockReporter.startLaunch();
	}

	@Override
	public void visitSpec(SpecInfo spec) {
		/*
		 * Spec is registered here, because in the case of error in the
		 * SHARED_INITIALIZER beforeSpec(...) on the listener isn't called
		 */
		spockReporter.registerSpec(spec);
		spec.addListener(new ReportableRunListener(spockReporter));
	}

	public void stop() {
		spockReporter.finishLaunch();
	}
}
