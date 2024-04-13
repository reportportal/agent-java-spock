package com.epam.reportportal.spock.features.fail

import spock.lang.Specification

class FailWithExceptionWithoutMessage extends Specification {

    def "should demonstrate NoSuchElementException given-when-then fail"() {
        given:
        def polygon = new ArrayList()

        when:
        polygon.size()

        then:
        throw new NoSuchElementException()
    }
}