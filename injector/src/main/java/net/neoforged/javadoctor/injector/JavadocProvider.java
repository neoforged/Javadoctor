package net.neoforged.javadoctor.injector;

import net.neoforged.javadoctor.spec.ClassJavadoc;
import org.jetbrains.annotations.Nullable;

public interface JavadocProvider {
    @Nullable
    ClassJavadoc get(String className);
}
