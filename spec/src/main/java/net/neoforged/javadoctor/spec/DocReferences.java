package net.neoforged.javadoctor.spec;

import java.util.Map;

public class DocReferences {
    private final Map<String, String> classes;

    public DocReferences(Map<String, String> classes) {
        this.classes = classes;
    }

    public Map<String, String> getClasses() {
        return classes;
    }

    public String getInternalName(String className) {
        final String fromMap = classes.get(className);
        return fromMap == null ? className.replace('.', '/') : fromMap;
    }
}
