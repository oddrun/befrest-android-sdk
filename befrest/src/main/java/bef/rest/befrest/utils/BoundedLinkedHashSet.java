
package bef.rest.befrest.utils;

import java.util.LinkedHashSet;

class BoundedLinkedHashSet<E> extends LinkedHashSet<E> {
    private int maxCapacity;

    BoundedLinkedHashSet(int maxCapacity) {
        setMaxCapacity(maxCapacity);
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
