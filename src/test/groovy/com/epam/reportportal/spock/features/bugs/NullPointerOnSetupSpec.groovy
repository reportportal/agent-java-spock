package com.epam.reportportal.spock.features.bugs

import spock.lang.Specification

class NullPointerOnSetupSpec extends Specification {
    def setup() {
    }

    def "should add two numbers correctly"() {
        given:
        def a = null

        when:
        def b = a.first()

        then:
        assert true
    }
}
