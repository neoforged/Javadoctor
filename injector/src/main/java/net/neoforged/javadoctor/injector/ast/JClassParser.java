package net.neoforged.javadoctor.injector.ast;

import net.neoforged.javadoctor.injector.Result;
import net.neoforged.javadoctor.spec.JavadocEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface JClassParser {
    default Result<List<JClass>> parse(String classText) {
        return new Result<>(Arrays.asList("No default impl provided"));
    }

    default Result<List<JClass>> parseFromPath(String path, String text) {
        return parse(text);
    }

    default Map<String, JavadocEntry> processMethodMap(Map<String, JavadocEntry> map) {
        return map;
    }
}
