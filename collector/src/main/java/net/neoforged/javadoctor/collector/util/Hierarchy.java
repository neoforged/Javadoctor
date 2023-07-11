package net.neoforged.javadoctor.collector.util;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record Hierarchy(Types types, Names names) {
    public Stream<Element> onAllChildren(TypeElement type) {
        return Stream.concat(type.getEnclosedElements().stream(), types
                .directSupertypes(type.asType())
                .stream()
                .flatMap(m -> onAllChildren((TypeElement) types.asElement(m))));
    }

    public Optional<ExecutableElement> findMethod(TypeElement type, String methodName, String methodDesc, boolean allowConstructors) {
        return onAllChildren(type)
                .filter(e -> e.getKind() == ElementKind.METHOD || (allowConstructors && e.getKind() == ElementKind.CONSTRUCTOR))
                .map(ExecutableElement.class::cast)
                .filter(m -> names().getDesc(m).equals(methodDesc))
                .filter(m -> m.getSimpleName().toString().equals(methodName))
                .findFirst();
    }

    public Optional<VariableElement> findField(TypeElement type, String fieldName, String fieldDesc) {
        return onAllChildren(type)
                .filter(e -> e.getKind().isField())
                .map(VariableElement.class::cast)
                .filter(v -> names().getParamDescriptor(v.asType()).equals(fieldDesc))
                .filter(v -> v.getSimpleName().toString().equals(fieldName))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public <T extends Element> List<T> findChildrenOfType(Element parent, ElementKind kind) {
        return parent.getEnclosedElements().stream()
                .filter(e -> e.getKind() == kind)
                .map(e -> (T) e)
                .toList();
    }

    public static Stream<TypeElement> walkEnclosingClasses(TypeElement clazz) {
        if (clazz.getEnclosingElement() == null)
            return Stream.empty();
        return Stream.of(clazz.getEnclosingElement())
                .filter(e -> e.getKind().isClass() || e.getKind().isInterface())
                .map(TypeElement.class::cast)
                .flatMap(e -> Stream.concat(walkEnclosingClasses(e), Stream.of(e)));
    }

    public static Stream<TypeElement> walkChildren(TypeElement clazz) {
        return clazz.getEnclosedElements().stream()
                .filter(e -> e.getKind().isClass() || e.getKind().isInterface())
                .map(TypeElement.class::cast)
                .flatMap(e -> Stream.concat(walkChildren(e), Stream.of(e)).distinct());
    }

    public static TypeElement getTopLevel(TypeElement clazz) {
        TypeElement topLevel = clazz;
        while (topLevel.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            topLevel = (TypeElement) topLevel.getEnclosingElement();
        }
        return topLevel;
    }
}
