package net.neoforged.javadoctor.spec;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class ClassJavadoc {
    @Nullable
    private final JavadocEntry clazz;
    @Nullable
    private final Map<String, JavadocEntry> methods;
    @Nullable
    private final Map<String, JavadocEntry> fields;
    private final Map<String, ClassJavadoc> innerClasses;

    public ClassJavadoc() {
        this(null, null, null, null);
    }

    public ClassJavadoc(@Nullable JavadocEntry clazz, @Nullable Map<String, JavadocEntry> methods, @Nullable Map<String, JavadocEntry> fields, @Nullable Map<String, ClassJavadoc> innerClasses) {
        this.clazz = clazz;
        this.methods = methods;
        this.fields = fields;
        this.innerClasses = innerClasses == null ? new HashMap<>() : innerClasses;
    }

    @Nullable
    public JavadocEntry clazz() {
        return clazz;
    }

    @Nullable
    public Map<String, JavadocEntry> methods() {
        return methods;
    }

    @Nullable
    public Map<String, JavadocEntry> fields() {
        return fields;
    }

    public Map<String, ClassJavadoc> innerClasses() {
        return innerClasses;
    }

    public ClassJavadoc merge(@Nullable ClassJavadoc other) {
        if (other == null) return this;
        return new ClassJavadoc(
                clazz == null ? other.clazz : clazz,
                mergeMaps(JavadocEntry::merge, this.methods, other.methods),
                mergeMaps(JavadocEntry::merge, this.fields, other.fields),
                mergeMaps(ClassJavadoc::merge, this.innerClasses, other.innerClasses)
        );
    }

    public boolean isEmpty() {
        return clazz == null && methods == null && fields == null && innerClasses.isEmpty();
    }

    @SafeVarargs
    static <K, V> Map<K, V> mergeMaps(BiFunction<V, V, V> valueMerger, Map<K, V>... maps) {
        if (maps.length == 1) {
            return maps[0];
        }
        final Map<K, V> newMap = new HashMap<>();
        for (Map<K, V> map : maps) {
            if (map == null) continue;
            map.forEach((k, v) -> {
                final V oldValue = newMap.get(k);
                if (oldValue == null) {
                    newMap.put(k, v);
                } else {
                    newMap.replace(k, valueMerger.apply(oldValue, v));
                }
            });
        }
        return newMap;
    }

    @SafeVarargs
    static <T> List<T> mergeLists(List<T>... lists) {
        if (lists.length == 1) {
            return lists[0];
        }
        final List<T> list = new ArrayList<>();
        for (List<T> ts : lists) {
            if (ts != null) {
                list.addAll(ts);
            }
        }
        return list;
    }
}
