package net.neoforged.javadoctor.injector;

import net.neoforged.javadoctor.spec.ClassJavadoc;

import javax.annotation.Nullable;

public interface JavadocProvider {
    @Nullable
    ClassJavadoc get(String className);
}
