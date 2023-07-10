package net.neoforged.javadoctor.collector.util;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnnotationUtils {
    private final Types types;
    private final AnnotationMirror mirror;

    public AnnotationUtils(Types types, AnnotationMirror mirror) {
        this.types = types;
        this.mirror = mirror;
    }

    public List<TypeElement> getClasses(String key) {
        return mirror.getElementValues().entrySet()
                .stream()
                .filter(e -> e.getKey().getSimpleName().contentEquals(key))
                .map(e -> listOf(e.getValue(), o -> (TypeElement) types.asElement((TypeMirror) o)))
                .findFirst()
                .orElse(List.of());
    }

    private <T> List<T> listOf(AnnotationValue annotationValue, Function<Object, T> cast) {
        final Object val = annotationValue.getValue();
        if (val instanceof List<?>) {
            return ((List<?>) val).stream().map(o -> cast.apply(((AnnotationValue) o).getValue())).toList();
        }
        return List.of(cast.apply(val));
    }

    @Override
    public String toString() {
        return mirror.getElementValues().keySet().stream()
                .map(ExecutableElement::getClass).collect(Collectors.toSet()).toString();
    }
}
