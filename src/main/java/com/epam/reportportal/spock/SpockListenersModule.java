package com.epam.reportportal.spock;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * @author Dzmitry Mikhievich
 */
class SpockListenersModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ISpockReporter.class).toProvider(SpockReporterProvider.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	private AbstractLaunchContext launchContext() {
		return new DefaultLaunchContext();
	}
}
