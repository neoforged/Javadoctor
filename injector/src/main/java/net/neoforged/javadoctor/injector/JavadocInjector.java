package net.neoforged.javadoctor.injector;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithParameters;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.TypeSolverBuilder;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JavadocInjector {
    private final JavaParser parser;
    private final JavadocProvider javadocProvider;

    private JavadocInjector(JavaParser parser, JavadocProvider javadocProvider) {
        this.parser = parser;
        this.javadocProvider = javadocProvider;
    }

    public JavadocInjector(int version, JavadocProvider javadocProvider, Iterable<Path> classpath) throws IOException {
        this(new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.valueOf("JAVA_" + version))
                .setSymbolResolver(new SymbolResolverWithRecordSupport(
                        solver(classpath)
                ))), javadocProvider);
    }

    private static TypeSolver solver(Iterable<Path> paths) throws IOException {
        final TypeSolverBuilder builder = new TypeSolverBuilder()
                .withCurrentJRE();
        for (final Path path : paths) {
            builder.withJAR(path);
        }
        return builder.build();
    }

    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    public ParseResult<InjectionResult> injectDocs(
            String sourceIn,
            @Nullable int[] mappingIn
    ) {
        final ParseResult<CompilationUnit> result = parser.parse(sourceIn);
        if (result.isSuccessful()) {
            final CompilationUnit unit = result.getResult().get();
            final List<String> newSource = new ArrayList<>();
            for (final String line : sourceIn.split("\n")){
                newSource.add(line);
            }

            final AtomicInteger offset = new AtomicInteger();
            final int[] newMapping = new int[mappingIn == null ? 0 : mappingIn.length];
            for (final TypeDeclaration<?> type : unit.getTypes()) {
                final ClassJavadoc javadoc = javadocProvider.get(type.getFullyQualifiedName().orElse(type.getNameAsString()));
                if (javadoc == null) {
                    continue;
                }

                inject(newSource, type, javadoc, offset, newMapping);
            }
            return new ParseResult<>(new InjectionResult(newMapping, String.join("\n", newSource)), Collections.emptyList(), null);
        } else {
            return new ParseResult<>(null, result.getProblems(), result.getCommentsCollection().orElse(null));
        }
    }

    @SuppressWarnings("all")
    private void inject(List<String> newSource, TypeDeclaration<?> declaration, ClassJavadoc javadoc, AtomicInteger offset, int[] mapping) {
        if (javadoc.clazz() != null) {
            declaration.getRange()
                .map(r -> r.begin.line)
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
        final Map<String, JavadocEntry> methods = (javadoc.methods() == null ? new HashMap<String, JavadocEntry>() : javadoc.methods()).entrySet()
                .stream().collect(Collectors.toMap(e -> e.getKey().replace('$', '/'), Map.Entry::getValue)); // Yay JavaParser... inners use $ not /

        final List<BodyDeclaration<?>> members = new ArrayList<>();
        members.addAll(declaration.getMembers());
        if (declaration instanceof EnumDeclaration) {
            members.addAll(((EnumDeclaration) declaration).getEntries());
        }
        Collections.sort(members, Comparator.comparing(r -> r.getRange().map(l -> l.begin.line).orElse(-1)));

        final Consumer<BodyDeclaration<?>> memberConsumer = member -> {
            if (member instanceof FieldDeclaration) {
                final JavadocEntry entry = ((FieldDeclaration) member).getVariables().stream()
                        .map(v -> fields.get(v.getNameAsString()))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                if (entry == null) return;
                member.getRange()
                        .map(r -> r.begin.line)
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
            } else if (member instanceof EnumConstantDeclaration) {
                final JavadocEntry entry = fields.get(((EnumConstantDeclaration) member).getNameAsString());
                if (entry == null) return;
                member.getRange()
                        .map(r -> r.begin.line)
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
            } else if (member instanceof CallableDeclaration<?>) {
                final String desc;
                if (member instanceof MethodDeclaration) {
                    final MethodDeclaration m = (MethodDeclaration) member;
                    desc = m.getNameAsString() + m.toDescriptor();
                } else {
                    desc = "<init>" + ((ConstructorDeclaration) member).toDescriptor();
                }
                final CallableDeclaration<?> method = (CallableDeclaration<?>) member;
                final JavadocEntry entry = methods.get(desc);
                if (entry == null) return;
                member.getRange()
                        .map(r -> r.begin.line)
                        .ifPresent(line -> {
                            final int offsetLine = line + offset.get() - 1;
                            final DocFormatter.WithLength formatted = DocFormatter.formatDoc(
                                    findIndent(newSource.get(offsetLine)), entry,
                                    method.getParameters().stream()
                                            .map(NodeWithSimpleName::getNameAsString).collect(Collectors.toList()),
                                    getTypeParameters(method)
                            );
                            offset.incrementAndGet();
                            newSource.add(offsetLine, formatted.doc);
                            pushMappingFix(mapping, line, formatted.length);
                        });
            } else if (member instanceof TypeDeclaration<?>) {
                final TypeDeclaration<?> type = (TypeDeclaration<?>) member;
                final ClassJavadoc innerDoc = javadoc.innerClasses().get(type.getNameAsString());
                if (innerDoc != null) {
                    inject(newSource, type, innerDoc, offset, mapping);
                }
            }
        };

        for (final BodyDeclaration<?> member : members) {
            try {
                memberConsumer.accept(member);
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    private static List<String> getTypeParameters(Object declaration) {
        if (declaration instanceof NodeWithTypeParameters<?>) {
            return ((NodeWithTypeParameters<?>) declaration).getTypeParameters().stream()
                    .map(NodeWithSimpleName::getNameAsString).collect(Collectors.toList());
        }
        return null;
    }

    @Nullable
    private static List<String> getParameters(Object declaration) {
        if (declaration instanceof NodeWithParameters<?>) {
            return ((NodeWithParameters<?>) declaration).getParameters().stream()
                    .map(NodeWithSimpleName::getNameAsString).collect(Collectors.toList());
        }
        return null;
    }

    private static void pushMappingFix(int[] mapping, int start, int amount) {
        for (int i = 0; i < mapping.length; i += 2) {
            int line = mapping[i + 1];
            if (line >= start) {
                mapping[i + 1] += amount;
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

    private static String getDescriptor(Type type, Function<String, String> importGetter) {
        if (type.isVoidType()) {
            return "V";
        }
        if (type.isPrimitiveType()) {
            return type.toPrimitiveType().get().toDescriptor();
        } else if (type.isArrayType()) {
            return "[" + getDescriptor(type.asArrayType().getComponentType(), importGetter);
        } else {
            return "L" + importGetter.apply(type.toString()).replace('.', '/') + ";";
        }
    }

    public static final class InjectionResult {
        @Nullable
        public final int[] mapping;
        public final String newSource;

        public InjectionResult(@Nullable int[] mapping, String newSource) {
            this.mapping = mapping;
            this.newSource = newSource;
        }
    }
}
