package net.neoforged.javadoctor.collector;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import net.neoforged.javadoctor.collector.util.AnnotationUtils;
import net.neoforged.javadoctor.collector.util.Hierarchy;
import net.neoforged.javadoctor.collector.util.Names;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.jetbrains.annotations.Nullable;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavadocCollector {
    public static final Pattern GENERIC = Pattern.compile("<(.+)>");
    public static final Pattern LINKS = Pattern.compile("(@link|@linkplain|@see|@value) ([a-zA-Z0-9\\._]+)");

    private final Types types;
    private final Messager messager;
    private final Elements elements;
    private final Trees trees;

    private final Names names;

    final Map<String, ClassJavadoc> javadocs = new HashMap<>();

    public JavadocCollector(Types types, Messager messager, Elements elements, Trees trees) {
        this.types = types;
        this.messager = messager;
        this.elements = elements;
        this.trees = trees;

        this.names = new Names(types, elements);
    }

    public void collectMixin(TypeElement typeElement, MixinTypes types) {
        final Imports imports = Imports.fromTree(trees.getPath(typeElement), typeElement);
        final AnnotationUtils mixinAn = types.getAnnotation(typeElement, types.Mixin);
        final List<TypeElement> mixind = mixinAn.getClasses("value");
        typeElement.getEnclosedElements().forEach(element -> {
            final Map<String, JavadocEntry> methods = new HashMap<>();
            final Map<String, JavadocEntry> fields = new HashMap<>();
            if ((element.getKind() == ElementKind.METHOD && (types.getAnnotation(element, types.Shadow) != null || types.getAnnotation(element, types.Unique) != null)) || element.getKind() == ElementKind.CONSTRUCTOR) {
                final ExecutableElement executableElement = (ExecutableElement) element;
                methods.put(
                        executableElement.getSimpleName().toString() + names.getDesc(executableElement),
                        createJavadoc(executableElement, imports, ParameterProvider.provider(executableElement.getParameters()), ParameterProvider.provider(executableElement.getTypeParameters()))
                );
            } else if (element.getKind() == ElementKind.FIELD && (types.getAnnotation(element, types.Shadow) != null || types.getAnnotation(element, types.Unique) != null)) {
                fields.put(
                        element.getSimpleName().toString() + ":" + names.getParamDescriptor(element.asType()),
                        createJavadoc(element, imports, null, null)
                );
            }

            if (!methods.isEmpty() || !fields.isEmpty()) {
                mixind.forEach(mx -> mergeWithExisting(mx, new ClassJavadoc(null, methods.isEmpty() ? null : methods, fields.isEmpty() ? null : fields, null)));
            }
        });
    }

    public void mergeWithExisting(TypeElement type, ClassJavadoc doc) {
        final List<TypeElement> parents = Stream.concat(Hierarchy.walkEnclosingClasses(type), Stream.of(type)).collect(Collectors.toCollection(ArrayList::new));
        ClassJavadoc parentDoc = null;
        for (final TypeElement parent : parents) {
            if (parentDoc == null) {
                parentDoc = javadocs.get(parent.getQualifiedName().toString());
            } else {
                parentDoc = parentDoc.innerClasses().get(parent.getSimpleName().toString());
                if (parentDoc == null) {
                    break;
                }
            }
        }

        final ClassJavadoc newDoc = doc.merge(parentDoc);
        final ClassJavadoc root = javadocs.computeIfAbsent(parents.get(0).getQualifiedName().toString(), s -> new ClassJavadoc());
        ClassJavadoc toMergeWith = root;
        Map<String, ClassJavadoc> inners = root.innerClasses();
        for (int i = 1; i < parents.size(); i++) {
            inners = toMergeWith.innerClasses();
            toMergeWith = toMergeWith.innerClasses().computeIfAbsent(parents.get(i).getSimpleName().toString(), s -> new ClassJavadoc());
        }
        if (parents.size() == 1) {
            javadocs.put(parents.get(0).getQualifiedName().toString(), newDoc);
        } else {
            inners.put(parents.get(parents.size() - 1).getSimpleName().toString(), doc);
        }
    }

    public void collect(TypeElement typeElement) {
        final ClassJavadoc classJavadoc = buildClass(typeElement);
        if (!classJavadoc.isEmpty()) {
            javadocs.put(typeElement.getQualifiedName().toString(), classJavadoc);
        }
    }

    public ClassJavadoc buildClass(TypeElement clazz) {
        final Imports imports = Imports.fromTree(trees.getPath(clazz), clazz);
        final JavadocEntry clazzdoc = createJavadoc(clazz, imports, clazz.getKind() == ElementKind.RECORD ? ParameterProvider.provider(ElementFilter.recordComponentsIn(clazz.getEnclosedElements())) : null, ParameterProvider.provider(clazz.getTypeParameters()));
        Map<String, JavadocEntry> methods = new HashMap<>();
        Map<String, JavadocEntry> fields = new HashMap<>();
        Map<String, ClassJavadoc> innerClasses = new HashMap<>();

        clazz.getEnclosedElements().forEach(element -> {
            if (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR) {
                final ExecutableElement executableElement = (ExecutableElement) element;
                methods.put(
                        executableElement.getSimpleName().toString() + names.getDesc(executableElement),
                        createJavadoc(executableElement, imports, ParameterProvider.provider(executableElement.getParameters()), ParameterProvider.provider(executableElement.getTypeParameters()))
                );
            } else if (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.ENUM_CONSTANT) {
                fields.put(
                        element.getSimpleName().toString() + ":" + names.getParamDescriptor(element.asType()),
                        createJavadoc(element, imports, null, null)
                );
            } else if (element instanceof TypeElement typeElement) {
                final ClassJavadoc innerDoc = buildClass(typeElement);
                if (!innerDoc.isEmpty())
                    innerClasses.put(typeElement.getSimpleName().toString(), innerDoc);
            }
        });

        methods.values().removeIf(Objects::isNull);
        fields.values().removeIf(Objects::isNull);

        return new ClassJavadoc(clazzdoc, methods.isEmpty() ? null : methods, fields.isEmpty() ? null : fields, innerClasses);
    }

    // TODO - parameters: maybe replace `{@code <paramname>}` with `{@code param<index>}` to be then replaced with the actual param name when injected.
    @Nullable
    private JavadocEntry createJavadoc(Element collectingElement, Imports imports, @Nullable ParameterProvider paramsGetter, @Nullable ParameterProvider genericParamsGetter) {
        String docComment = elements.getDocComment(collectingElement);
        if (docComment == null || docComment.isBlank()) return null;
        docComment = DocFQNExpander.expand(collectingElement instanceof TypeElement typeElement ? typeElement : (TypeElement) collectingElement.getEnclosingElement(), docComment, imports);
        docComment = LINKS.matcher(docComment).replaceAll(matchResult -> matchResult.group(1) + " " + imports.getQualified(matchResult.group(2)));
        final List<String> docs = new ArrayList<>();
        Map<String, List<String>> tags = new HashMap<>();
        final var params = walk(docComment, new JDocWalker<Map.Entry<String[], String[]>>() {
            String[] parameters = null;
            String[] typeParameters = null;

            @Override
            public void onLine(String line) {
                docs.add(line);
            }

            @Override
            public void onTag(String tag, String line) {
                if (tag.equals("param")) {
                    final String[] splitWithParam = line.split(" ", 2);
                    if (splitWithParam.length != 2) {
                        messager.printMessage(Diagnostic.Kind.WARNING, "Found incomplete param tag!", collectingElement);
                        return;
                    }
                    final String paramName = splitWithParam[0];
                    final Matcher generic = GENERIC.matcher(paramName);
                    if (generic.find()) {
                        if (genericParamsGetter == null) {
                            messager.printMessage(Diagnostic.Kind.WARNING, "Found generic parameter but the element does not support generic parameters!", collectingElement);
                        } else {
                            final int idx = genericParamsGetter.getIndex(generic.group(1));
                            if (idx == -1) {
                                messager.printMessage(Diagnostic.Kind.WARNING, "Unknown generic parameter named '" + generic.group(1) + "'", collectingElement);
                            } else {
                                (typeParameters == null ? (typeParameters = genericParamsGetter.provide()) : typeParameters)[idx] = splitWithParam[1];
                            }
                        }
                    } else {
                        if (paramsGetter == null) {
                            messager.printMessage(Diagnostic.Kind.WARNING, "Found named parameter but the element does not support named parameters!", collectingElement);
                        } else {
                            final int idx = paramsGetter.getIndex(paramName);
                            if (idx == -1) {
                                messager.printMessage(Diagnostic.Kind.WARNING, "Unknown parameter named '" + paramName + "'", collectingElement);
                            } else {
                                (parameters == null ? (parameters = paramsGetter.provide()) : parameters)[idx] = splitWithParam[1];
                            }
                        }
                    }
                } else {
                    if (tag.equals("deprecated") && line.isBlank()) return; // FF loves to add emtpy @deprecated tags
                    tags.computeIfAbsent(tag, k -> new ArrayList<>()).add(line);
                }
            }

            @Override
            public Map.Entry<String[], String[]> finish() {
                return new AbstractMap.SimpleEntry<>(parameters, typeParameters);
            }
        });
        final String finalDoc = String.join("\n", docs).trim();
        final JavadocEntry entry = new JavadocEntry(finalDoc.isBlank() ? null : finalDoc, tags.isEmpty() ? null : tags, params.getKey(), params.getValue());
        return entry.isEmpty() ? null : entry;
    }

    private <T> T walk(String comment, JDocWalker<T> walker) {
        StringBuilder current = new StringBuilder();
        String tagName = null;
        final String[] lines = comment.split("\n");
        if (lines.length == 0) return walker.finish();
        int indentAmount = 0;
        final String initialLine = lines[0];
        for (int i = 0; i < initialLine.length(); i++) {
            if (initialLine.charAt(i) == ' ') {
                indentAmount++;
            } else {
                break;
            }
        }

        for (String line : lines) {
            if (line.length() >= indentAmount) {
                line = line.substring(indentAmount);
            }

            if (line.startsWith("@")) {
                final String[] split = line.substring(1).split(" ", 2);
                if (split.length == 2) {
                    if (tagName != null) {
                        walker.onTag(tagName, current.toString().trim());
                    }
                    tagName = split[0];
                    current = new StringBuilder().append(split[1]);
                    continue;
                }
            }

            if ((line.isEmpty() || line.charAt(0) == ' ') && tagName != null) {
                current.append(line.trim());
            } else {
                if (tagName != null) {
                    walker.onTag(tagName, current.toString().trim());
                    current = null;
                    tagName = null;
                }
                walker.onLine(line.trim());
            }
        }

        if (tagName != null) {
            walker.onTag(tagName, current.toString().trim());
        }
        return walker.finish();
    }

    public interface ParameterProvider {
        String[] provide();
        int getIndex(String name);

        static ParameterProvider provider(List<? extends Element> generics) {
            return new ParameterProvider() {
                @Override
                public String[] provide() {
                    final String[] array = new String[generics.size()];
                    Arrays.fill(array, null);
                    return array;
                }

                private List<String> parameterTypes;
                @Override
                public int getIndex(String name) {
                    return (parameterTypes == null ? (parameterTypes = generics.stream()
                            .map(t -> t.getSimpleName().toString()).toList()) : parameterTypes).indexOf(name);
                }
            };
        }

    }

    public interface JDocWalker<T> {
        void onLine(String line);
        void onTag(String tag, String line);
        T finish();
    }

    public interface Imports {
        String getQualified(String inputName);
        static Imports fromTree(TreePath tree, TypeElement owner) {
            final Supplier<Map<String, String>> imports = memoized(() -> {
                final Map<String, String> i = new HashMap<>();
                tree.getCompilationUnit().getImports().forEach(importTree -> {
                    final String name = importTree.getQualifiedIdentifier().toString();
                    if (importTree.isStatic()) {
                        i.put("#" + name.substring(name.lastIndexOf('.') + 1), name);
                    } else {
                        i.put(name.substring(name.lastIndexOf('.') + 1), name);
                    }
                });
                return i;
            });
            final TypeElement finalTopLevel = Hierarchy.getTopLevel(owner);
            final Supplier<Map<String, TypeElement>> topChildren = memoized(() -> Stream.concat(Stream.of(finalTopLevel), Hierarchy.walkChildren(finalTopLevel))
                    .collect(Collectors.toMap(it -> it.getSimpleName().toString(), Function.identity())));
            return inputName -> {
                final String last = inputName.substring(0, lastReferenceLocation(inputName));
                final String imp = imports.get().get(last);
                if (imp != null) {
                    return imp;
                }
                if (!inputName.startsWith("#")) {
                    final TypeElement child = topChildren.get().get(last); // TODO - this isn't flawless, it will find the wrong one when there's a lot of nesting of classes with the same name
                    if (child != null) {
                        return child.getQualifiedName().toString();
                    }
                }

                // Fallback to the default imports
                try {
                    return Class.forName("java.lang." + inputName).getName();
                } catch (Exception exception) {
                    return inputName;
                }
            };
        }
    }

    static <T> Supplier<T> memoized(Supplier<T> value) {
        return new Supplier<>() {
            T val;
            @Override
            public T get() {
                return (val == null ? (val = value.get()) : val);
            }
        };
    }

    private static int lastReferenceLocation(String str) {
        int idx = str.indexOf('.');
        if (idx == -1) {
            idx = str.indexOf('#');
            if (idx == 0) {
                return str.length();
            }
        }
        return idx == -1 ? str.length() : idx;
    }
}
