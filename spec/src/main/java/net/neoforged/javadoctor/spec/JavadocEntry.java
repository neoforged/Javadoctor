package net.neoforged.javadoctor.spec;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class JavadocEntry {
    @Nullable
    private final String doc;
    @Nullable
    private final Map<String, List<String>> tags;
    @Nullable
    private final String[] parameters;
    @Nullable
    private final String[] typeParameters;

    public JavadocEntry(@Nullable String doc, @Nullable Map<String, List<String>> tags, @Nullable String[] parameters, @Nullable String[] typeParameters) {
        this.doc = doc;
        this.tags = tags;
        this.parameters = parameters;
        this.typeParameters = typeParameters;
    }

    @Nullable
    public String doc() {
        return doc;
    }

    @Nullable
    public Map<String, List<String>> tags() {
        return tags;
    }

    @Nullable
    public String[] parameters() {
        return parameters;
    }

    @Nullable
    public String[] typeParameters() {
        return typeParameters;
    }

    public JavadocEntry merge(JavadocEntry other) {
        return new JavadocEntry(
                doc == null ? other.doc : doc,
                ClassJavadoc.mergeMaps(ClassJavadoc::mergeLists, this.tags, other.tags),
                mergeParams(this.parameters, other.parameters),
                mergeParams(this.typeParameters, other.typeParameters)
        );
    }

    public boolean isEmpty() {
        return doc == null && (tags == null || tags.isEmpty()) && parameters == null && typeParameters == null;
    }

    @Nullable
    private static String[] mergeParams(@Nullable String[] a, @Nullable String[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }

        if (a.length != b.length) {
            throw new IllegalArgumentException("Param arrays must be of the same length!");
        }
        final String[] newS = new String[a.length];
        for (int i = 0; i < a.length; i++) {
            final String fa = a[i];
            if (fa == null) {
                newS[i] = b[i];
            } else {
                newS[i] = fa;
            }
        }
        return newS;
    }
}
