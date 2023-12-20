package net.neoforged.javadoctor.injector;

import net.neoforged.javadoctor.injector.ast.JClass;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import net.neoforged.javadoctor.injector.ast.JElement;
import net.neoforged.javadoctor.injector.ast.JField;
import net.neoforged.javadoctor.injector.ast.JMethod;
import net.neoforged.javadoctor.injector.ast.JParameter;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JavadocInjector {
    private final JClassParser parser;
    private final JavadocProvider javadocProvider;

    public JavadocInjector(JClassParser parser, JavadocProvider javadocProvider) {
        this.parser = parser;
        this.javadocProvider = javadocProvider;
    }

    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    public Result<InjectionResult> injectDocs(
            String path,
            String className,
            String sourceInIn,
            @Nullable int[] mappingIn
    ) {
        // Uh.. yes
        final String sourceIn = sourceInIn.replace("\r\n", "\n");
        if (javadocProvider.get(className) == null) return new Result<>(new InjectionResult(mappingIn, sourceIn));
        return parser.parseFromPath(path, sourceIn).map(classes -> {
            final List<String> newSource = new ArrayList<>();
            for (final String line : sourceIn.split("\n")) {
                newSource.add(line);
            }

            final AtomicInteger offset = new AtomicInteger();
            final List<Integer> newMapping = new ArrayList<>();
            if (mappingIn != null) {
                for (int i : mappingIn) {
                    newMapping.add(i);
                }
            }

            for (final JClass type : classes) {
                final ClassJavadoc javadoc = javadocProvider.get(type.getFullyQualifiedName());
                if (javadoc == null) {
                    continue;
                }

                inject(newSource, type, javadoc, offset, newMapping, mappingIn == null);
            }
            return new InjectionResult(newMapping.stream().mapToInt(i -> i).toArray(), String.join(System.lineSeparator(), newSource));
        });
    }

    @SuppressWarnings("all")
    private void inject(List<String> newSource, JClass declaration, ClassJavadoc javadoc, AtomicInteger offset, List<Integer> mapping, boolean appendLineMappings) {
        final List<JElement> members = new ArrayList<>();
        members.addAll(declaration.getChildren());
        Collections.sort(members, Comparator.comparing(r -> r.getSourceLine().orElse(-1)));

        if (appendLineMappings) {
            declaration.getSourceLine().ifPresent(line -> {
                mapping.add(line);
                mapping.add(line);
            });
            for (final JElement member : members) {
                member.getSourceLine().ifPresent(line -> {
                    mapping.add(line);
                    mapping.add(line);
                });
            }
        }

        if (javadoc.clazz() != null) {
            declaration.getSourceLine()
                .ifPresent(line -> {
                    final int offsetLine = line + offset.get() - 1;
                    final DocFormatter.WithLength formatted = DocFormatter.formatDoc(
                            findIndent(newSource.get(offsetLine)), javadoc.clazz(),
                            getParameters(declaration), getTypeParameters(declaration)
                    );
                    offset.incrementAndGet();
                    newSource.add(offsetLine, formatted.doc);
                    pushMappingFix(mapping, line, formatted.length);
                });
        }
        final Map<String, JavadocEntry> fields = (javadoc.fields() == null ? new HashMap<String, JavadocEntry>() : javadoc.fields()).entrySet()
                .stream().collect(Collectors.toMap(e -> e.getKey().split(":", 2)[0], Map.Entry::getValue));
        final Map<String, JavadocEntry> methods = (javadoc.methods() == null ? new HashMap<String, JavadocEntry>() : parser.processMethodMap(javadoc.methods()));

        final Consumer<JElement> memberConsumer = member -> {
            if (member instanceof JField) {
                final JavadocEntry entry = fields.get(member.getName());
                if (entry == null) return;
                member.getSourceLine()
                        .ifPresent(line -> {
                            final int offsetLine = line + offset.get() - 1;
                            final DocFormatter.WithLength formatted = DocFormatter.formatDoc(
                                    findIndent(newSource.get(offsetLine)), entry,
                                    null, null
                            );
                            offset.incrementAndGet();
                            newSource.add(offsetLine, formatted.doc);
                            pushMappingFix(mapping, line, formatted.length);
                        });
            } else if (member instanceof JMethod) {
                final JMethod method = (JMethod) member;
                final String desc = method.getDescriptor();
                final JavadocEntry entry = methods.get(desc);
                if (entry == null) return;
                member.getSourceLine()
                        .ifPresent(line -> {
                            final int offsetLine = line + offset.get() - 1;
                            final DocFormatter.WithLength formatted = DocFormatter.formatDoc(
                                    findIndent(newSource.get(offsetLine)), entry,
                                    method.getParameters().stream()
                                            .map(JParameter::getName).collect(Collectors.toList()),
                                    getTypeParameters(method)
                            );
                            offset.incrementAndGet();
                            newSource.add(offsetLine, formatted.doc);
                            pushMappingFix(mapping, line, formatted.length);
                        });
            } else if (member instanceof JClass) {
                final JClass type = (JClass) member;
                final ClassJavadoc innerDoc = javadoc.innerClasses().get(type.getName());
                if (innerDoc != null) {
                    inject(newSource, type, innerDoc, offset, mapping, appendLineMappings);
                }
            }
        };

        for (final JElement member : members) {
            try {
                memberConsumer.accept(member);
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    private static List<String> getTypeParameters(Object declaration) {
        if (declaration instanceof JElement.WithTypeParameters) {
            return ((JElement.WithTypeParameters) declaration).getTypeParameters().stream()
                    .map(JParameter::getName).collect(Collectors.toList());
        }
        return null;
    }

    @Nullable
    private static List<String> getParameters(Object declaration) {
        if (declaration instanceof JElement.WithParameters) {
            return ((JElement.WithParameters) declaration).getParameters().stream()
                    .map(JParameter::getName).collect(Collectors.toList());
        }
        return null;
    }

    private static void pushMappingFix(List<Integer> mapping, int start, int amount) {
        for (int i = 0; i < mapping.size(); i += 2) {
            int originalLine = mapping.get(i);
            if (originalLine >= start) {
                mapping.set(i + 1, mapping.get(i + 1) + amount);
            }
        }
    }

    private static String findIndent(String line) {
        final StringBuilder builder = new StringBuilder();
        for (final char ch : line.toCharArray()) {
            if (ch == '\t' || ch == ' ') {
                builder.append(ch);
            } else {
                break;
            }
        }
        return builder.toString();
    }

    public static final class InjectionResult {
        public final int @Nullable [] mapping;
        public final String newSource;

        public InjectionResult(int @Nullable [] mapping, String newSource) {
            this.mapping = mapping;
            this.newSource = newSource;
        }
    }

}
