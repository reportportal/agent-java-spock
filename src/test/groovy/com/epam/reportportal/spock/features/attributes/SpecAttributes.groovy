/*
 * Copyright 2021 EPAM Systems
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

package com.epam.reportportal.spock.features.attributes

import com.epam.reportportal.annotations.attribute.Attribute
import com.epam.reportportal.annotations.attribute.Attributes
import spock.lang.Specification

@Attributes(attributes = @Attribute(key = "key", value = "value"))
class SpecAttributes extends Specification {
    def "my empty fixture"() {
        expect:
        //noinspection GroovyPointlessBoolean
        true == true
    }
}
