package net.neoforged.javadoctor.injector;

import net.neoforged.javadoctor.spec.ClassJavadoc;

import java.util.ArrayList;
import java.util.List;

public class CombiningJavadocProvider implements JavadocProvider {
    private final List<JavadocProvider> sources;

    public CombiningJavadocProvider(List<JavadocProvider> sources) {
        this.sources = new ArrayList<>(sources);
    }

    @Override
    public ClassJavadoc get(String clazz) {
        ClassJavadoc javadoc = null;
        for (final JavadocProvider provider : sources) {
            if (javadoc == null) {
                javadoc = provider.get(clazz);
            } else {
                javadoc = javadoc.merge(provider.get(clazz));
            }
        }
        return javadoc;
    }
}
