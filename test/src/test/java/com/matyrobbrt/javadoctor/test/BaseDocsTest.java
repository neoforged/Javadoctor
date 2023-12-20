package com.matyrobbrt.javadoctor.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import spoon.Launcher;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class BaseDocsTest {
    protected static FileSystem spoonFs;
    protected static FileSystem javaparseFs;
    protected static FileSystem jbPsiFs;
    protected static Map<DocSystem, FileSystem> systems;

    @BeforeAll
    static void setupFs() throws IOException {
        spoonFs = open("spoonJar");
        javaparseFs = open("javaparserJar");
        jbPsiFs = open("jbPsiJar");

        systems = new EnumMap<>(DocSystem.class);
        systems.put(DocSystem.SPOON, spoonFs);
        systems.put(DocSystem.JAVAPARSER, javaparseFs);
        systems.put(DocSystem.JB_PSI, jbPsiFs);
    }

    private static FileSystem open(String propertyName) throws IOException {
        final String path = System.getProperty(propertyName);
        final FileSystem fs = FileSystems.newFileSystem(Path.of(path));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                fs.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        return fs;
    }

    protected static ClassFile file(String name) throws IOException {
        final Map<DocSystem, List<Clazz>> classes = new EnumMap<>(DocSystem.class);
        for (final var sys : systems.entrySet()) {
            final Launcher launcher = new Launcher();
            launcher.getEnvironment().setComplianceLevel(17);
            final String text = Files.readString(sys.getValue().getPath(name));
            launcher.addInputResource(new VirtualFile(text));

            classes.put(sys.getKey(), launcher.buildModel().getAllTypes().stream().map(it -> new Clazz(it, text)).collect(Collectors.toList()));
        }
        return new ClassFile(classes);
    }

    protected record ClassFile(Map<DocSystem, List<Clazz>> types) {
        public ClassPair getClassByName(String name) {
            return new ClassPair(types.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, cls -> cls.getValue().stream()
                            .filter(c -> c.type.getSimpleName().equals(name))
                            .findFirst()
                            .orElseThrow())), null);
        }
    }

    public record ClassPair(Map<DocSystem, Clazz> bySystem, ClassPair parent) {
        public ClassPair assertDocOfMethodMatches(String method, String... doc) {
            final String finalDoc = String.join("\n", doc) + "\n"; // Docs have a leading \n
            bySystem.forEach((sys, clz) -> {
                Assertions.assertEquals(finalDoc, clz.getJavadoc(method), () -> "Javadoc of method '" + method + "' of system " + sys + " of class '" + clz.type.getQualifiedName() + "' is not the expected one!");
            });
            return this;
        }

        public ClassPair assertDocOfFieldMatches(String method, String... doc) {
            final String finalDoc = String.join("\n", doc) + "\n"; // Docs have a leading \n
            bySystem.forEach((sys, clz) -> {
                Assertions.assertEquals(finalDoc, clz.getFieldJavadoc(method), () -> "Javadoc of field '" + method + "' of system " + sys + " of class '" + clz.type.getQualifiedName() + "' is not the expected one!");
            });
            return this;
        }

        public ClassPair assertClassDocMatches(String... doc) {
            final String finalDoc = String.join("\n", doc) + "\n"; // Docs have a leading \n
            bySystem.forEach((sys, clz) -> {
                Assertions.assertEquals(finalDoc, clz.getClassJavadoc(), () -> "Javadoc of class '" + clz.type.getQualifiedName() + "' of system " + sys + " is not the expected one!");
            });
            return this;
        }

        public ClassPair getInner(String name) {
            return new ClassPair(bySystem.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, it -> new Clazz(it.getValue()
                            .type.getNestedType(name), it.getValue().source))), this);
        }
    }

    private static String[] keepParsing(String source, int start) {
        StringBuilder content = new StringBuilder();
        boolean inside = false;
        for (int i = start; i < source.length(); i++) {
            if (source.charAt(i) == '/' && i < source.length() - 1) {
                if (source.charAt(i + 1) == '*' && source.charAt(i + 2) == '*' && !inside) {
                    inside = true;
                    i += 2;
                    continue;
                } else if (inside && source.charAt(i - 1) == '*') {
                    break;
                }
            }

            if (inside) {
                content.append(source.charAt(i));
            }
        }

        return Arrays.stream(content.toString().split("\n"))
                .filter(Predicate.not(String::isBlank))
                .map(String::stripLeading)
                .map(str -> str.charAt(0) == '*' ? str.substring(str.length() == 1 ? 1 : 2) : str)
                .toArray(String[]::new);
    }

    protected record Clazz(CtType<?> type, String source) {
        public String getJavadoc(String method) {
            return String.join("\n", keepParsing(source, type.getMethodsByName(method)
                    .stream().findFirst()
                    .orElseThrow()
                    .getPosition().getSourceStart()));
        }

        public String getFieldJavadoc(String field) {
            return String.join("\n", keepParsing(source, type.getField(field).getPosition().getSourceStart()));
        }

        public String getClassJavadoc() {
            return String.join("\n", keepParsing(source, type.getPosition().getSourceStart()));
        }
    }

    public enum DocSystem {
        SPOON,
        JAVAPARSER,
        JB_PSI
    }
}
