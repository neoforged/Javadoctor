package net.neoforged.javadoctor.injector.ast;

import net.neoforged.javadoctor.injector.Result;
import net.neoforged.javadoctor.spec.JavadocEntry;

import java.util.List;
import java.util.Map;

public interface JClassParser {
    Result<List<JClass>> parse(String classText);
    default Map<String, JavadocEntry> processMethodMap(Map<String, JavadocEntry> map) {
        return map;
    }
}
