package net.neoforged.javadoctor.injector.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import net.neoforged.javadoctor.injector.Result;
import net.neoforged.javadoctor.injector.ast.JClass;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import net.neoforged.javadoctor.injector.ast.JElement;
import net.neoforged.javadoctor.injector.ast.JField;
import net.neoforged.javadoctor.injector.ast.JMethod;
import net.neoforged.javadoctor.injector.ast.JParameter;
import net.neoforged.javadoctor.injector.ast.JRecord;
import net.neoforged.javadoctor.spec.JavadocEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JavaparserClassParser implements JClassParser {
    private final JavaParser parser;

    public JavaparserClassParser(JavaParser parser) {
        this.parser = parser;
    }

    @Override
    public Result<List<JClass>> parse(String classText) {
        final ParseResult<CompilationUnit> result = parser.parse(classText);
        if (result.isSuccessful()) {
            final CompilationUnit unit = result.getResult().get();
            return new Result<>(
                    unit.getTypes().stream().map(this::createClass).collect(Collectors.toList())
            );
        } else {
            return new Result<>(result.getProblems().stream()
                    .map(Problem::toString).collect(Collectors.toList()));
        }
    }

    @Override
    public Map<String, JavadocEntry> processMethodMap(Map<String, JavadocEntry> map) {
        return map.entrySet()
                .stream().collect(Collectors.toMap(e -> e.getKey().replace('$', '/'), Map.Entry::getValue)); // Yay JavaParser... inners use $ not /
    }

    private JClass createClass(TypeDeclaration<?> declaration) {
        if (declaration instanceof RecordDeclaration) {
            return new BaseRecord((RecordDeclaration) declaration);
        }
        return new BaseClass<>(declaration);
    }

    private JField createField(String name, Optional<Range> pos) {
        return new JField() {
            @Override
            public OptionalInt getSourceLine() {
                return pos.map(range -> OptionalInt.of(range.begin.line)).orElseGet(OptionalInt::empty);
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String toString() {
                return "JField[name=" + name + "]";
            }
        };
    }

    private class BaseRecord extends BaseClass<RecordDeclaration> implements JRecord {

        private BaseRecord(RecordDeclaration declaration) {
            super(declaration);
        }

        @Override
        public List<JParameter> getParameters() {
            return declaration.getParameters().stream()
                    .map(it -> (JParameter) it::getNameAsString).collect(Collectors.toList());
        }
    }

    private class BaseClass<T extends TypeDeclaration<?>> implements JClass {
        protected final T declaration;
        private final List<JElement> children;

        private BaseClass(T declaration) {
            this.declaration = declaration;
            this.children = declaration.getMembers().stream()
                    .map(it -> {
                        if (it instanceof FieldDeclaration) {
                            return createField(((FieldDeclaration) it).getVariable(0).getNameAsString(), it.getRange());
                        } else if (it instanceof CallableDeclaration<?>) {
                            final CallableDeclaration<?> method = (CallableDeclaration<?>) it;
                            final boolean ctor;
                            final Supplier<String> desc;
                            if (method instanceof MethodDeclaration) {
                                final MethodDeclaration m = (MethodDeclaration) method;
                                desc = () -> m.getNameAsString() + m.toDescriptor();
                                ctor = false;
                            } else {
                                desc = () -> "<init>" + ((ConstructorDeclaration) method).toDescriptor();
                                ctor = true;
                            }
                            return new JMethod() {
                                @Override
                                public String getDescriptor() {
                                    return desc.get();
                                }

                                @Override
                                public boolean isConstructor() {
                                    return ctor;
                                }

                                @Override
                                public OptionalInt getSourceLine() {
                                    return method.getRange().map(range -> OptionalInt.of(range.begin.line)).orElseGet(OptionalInt::empty);
                                }

                                @Override
                                public String getName() {
                                    return method.getNameAsString();
                                }

                                @Override
                                public List<JParameter> getParameters() {
                                    return method.getParameters().stream()
                                            .map(t -> (JParameter) t::getNameAsString).collect(Collectors.toList());
                                }

                                @Override
                                public List<JParameter> getTypeParameters() {
                                    return method.getTypeParameters().stream()
                                            .map(t -> (JParameter) t::getNameAsString).collect(Collectors.toList());
                                }
                            };
                        } else if (it instanceof TypeDeclaration<?>) {
                            return createClass((TypeDeclaration<?>) it);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (declaration instanceof EnumDeclaration) {
                ((EnumDeclaration) declaration).getEntries().forEach(enumField ->
                        children.add(createField(enumField.getNameAsString(), enumField.getRange())));
            }
        }

        @Override
        public String getFullyQualifiedName() {
            return declaration.getFullyQualifiedName().orElseGet(this::getName);
        }

        @Override
        public List<JElement> getChildren() {
            return children;
        }

        @Override
        public OptionalInt getSourceLine() {
            if (declaration.getRange().isPresent()) {
                return OptionalInt.of(declaration.getRange().get().begin.line);
            } else {
                return OptionalInt.empty();
            }
        }

        @Override
        public String getName() {
            return declaration.getNameAsString();
        }

        @Override
        public List<JParameter> getTypeParameters() {
            if (declaration instanceof NodeWithTypeParameters<?>) {
                return ((NodeWithTypeParameters<?>) declaration).getTypeParameters().stream()
                        .map(t -> (JParameter) t::getNameAsString).collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }
}
