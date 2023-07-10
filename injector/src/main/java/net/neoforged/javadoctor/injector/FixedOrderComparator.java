package net.neoforged.javadoctor.injector;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FixedOrderComparator<T> implements Comparator<T> {
    private final List<T> order;

    private FixedOrderComparator(List<T> order) {
        this.order = order;
    }

    @SafeVarargs
    public static <T> Comparator<T> of(T... values) {
        return new FixedOrderComparator<>(Stream.of(values).collect(Collectors.toList()));
    }

    @Override
    public int compare(T o1, T o2) {
        return Integer.compare(indexOf(o1), indexOf(o2));
    }

    public int indexOf(T value) {
        final int idx = order.indexOf(value);
        return idx == -1 ? Integer.MAX_VALUE : idx;
    }
}
