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
