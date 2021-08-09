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

import com.epam.reportportal.listeners.ItemStatus;
import io.reactivex.Maybe;
import org.spockframework.runtime.model.NodeInfo;
import rp.com.google.common.base.Optional;
import rp.com.google.common.base.Predicate;

import static rp.com.google.common.base.Preconditions.checkArgument;


/**
 * Base entity which stores the reporting metadata for the <i>Spock</i> test elements
 *
 * @author Dzmitry Mikhievich
 */
abstract class ReportableItemFootprint<T extends NodeInfo> {

    static final Predicate<ReportableItemFootprint> IS_NOT_PUBLISHED = input -> input != null && !input.isPublished();

    private final Maybe<String> id;
    private final T item;

    private ItemStatus status;
    private boolean published = false;

    ReportableItemFootprint(T item, Maybe<String> id) {
        checkArgument(item != null, "Node info shouldn't be null");
        checkArgument(id != null, "Test item id shouldn't be null");

        this.id = id;
        this.item = item;
    }

    T getItem() {
        return item;
    }

    Maybe<String> getId() {
        return id;
    }

    Optional<ItemStatus> getStatus() {
        return Optional.fromNullable(status);
    }

    void setStatus(ItemStatus status) {
        this.status = status;
    }

    void markAsPublished() {
        this.published = true;
    }

    boolean isPublished() {
        return published;
    }

    String getItemName() {
        return getItem().getName();
    }

    abstract boolean hasDescendants();
}
