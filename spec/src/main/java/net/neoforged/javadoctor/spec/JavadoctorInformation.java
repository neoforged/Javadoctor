package net.neoforged.javadoctor.spec;

import java.util.Map;

public class JavadoctorInformation {
    private final DocReferences references;
    private final Map<String, ClassJavadoc> classDocs;

    public JavadoctorInformation(DocReferences references, Map<String, ClassJavadoc> classDocs) {
        this.references = references;
        this.classDocs = classDocs;
    }

    public DocReferences getReferences() {
        return references;
    }

    public Map<String, ClassJavadoc> getClassDocs() {
        return classDocs;
    }
}
