/******************************************************************************
 * Copyright 2015-2016 Befrest
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package bef.rest;

import java.util.Collection;
import java.util.LinkedHashSet;

class BoundedLinkedHashSet<E> extends LinkedHashSet<E> {
    int maxCapacity;

    public BoundedLinkedHashSet(int maxCapacity) {
        setMaxCapacity(maxCapacity);
    }

    public BoundedLinkedHashSet(int maxCapacity, int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        setMaxCapacity(maxCapacity);
    }

    public BoundedLinkedHashSet(int maxCapacity, int initialCapacity) {
        super(initialCapacity);
        setMaxCapacity(maxCapacity);
    }

    public BoundedLinkedHashSet(int maxCapacity, Collection<? extends E> c) {
        setMaxCapacity(maxCapacity);
        addAll(c);
    }

    private void setMaxCapacity(int maxCapacity) {
        if (maxCapacity < 1)
            throw new IllegalArgumentException("maxCapacity should be more than zero");
        this.maxCapacity = maxCapacity;
    }

    @Override
    public boolean add(E e) {
        if (size() == maxCapacity && !contains(e))
            removeFirst();
        return super.add(e);
    }

    private void removeFirst() {
        remove(iterator().next());
    }
}
