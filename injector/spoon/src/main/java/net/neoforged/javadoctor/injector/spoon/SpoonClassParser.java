package net.neoforged.javadoctor.injector.spoon;

import net.neoforged.javadoctor.injector.Result;
import net.neoforged.javadoctor.injector.ast.JClass;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import net.neoforged.javadoctor.injector.ast.JElement;
import net.neoforged.javadoctor.injector.ast.JField;
import net.neoforged.javadoctor.injector.ast.JMethod;
import net.neoforged.javadoctor.injector.ast.JParameter;
import net.neoforged.javadoctor.injector.ast.JRecord;
import spoon.Launcher;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtRecord;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpoonClassParser implements JClassParser {
    private final Supplier<Launcher> launcherFactory;

    public SpoonClassParser(Supplier<Launcher> launcherFactory) {
        this.launcherFactory = launcherFactory;
    }

    @Override
    public Result<List<JClass>> parse(String classText) {
        final Launcher launcher = launcherFactory.get();
        launcher.addInputResource(new VirtualFile(classText));
        return new Result<>(launcher.buildModel().getAllTypes().stream().map(this::createClass).collect(Collectors.toList()));
    }

    private JClass createClass(CtType<?> declaration) {
        if (declaration instanceof CtRecord) {
            return new BaseRecord((CtRecord) declaration);
        }
        return new BaseClass<>(declaration);
    }

    private JField createField(String name, SourcePosition pos) {
        return new JField() {
            private int linePos = -1;
            @Override
            public OptionalInt getSourceLine() {
                if (linePos == -1) {
                    linePos = searchLineNumber(pos.getCompilationUnit(), pos.getSourceStart());
                }
                return OptionalInt.of(linePos);
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

    private class BaseRecord extends BaseClass<CtRecord> implements JRecord {

        private BaseRecord(CtRecord declaration) {
            super(declaration);
        }

        @Override
        public List<JParameter> getParameters() {
            return declaration.getRecordComponents().stream()
                    .map(it -> (JParameter) it::getSimpleName).collect(Collectors.toList());
        }
    }

    private class BaseClass<T extends CtType<?>> implements JClass {
        protected final T declaration;
        private final List<JElement> children;

        private BaseClass(T declaration) {
            this.declaration = declaration;
            this.children = new ArrayList<>();
            declaration.getFields().forEach(nonImplicit(field ->
                    children.add(createField(field.getSimpleName(), field.getPosition()))));
            Stream.concat(declaration.getMethods().stream(), (declaration instanceof CtClass<?> ? ((CtClass<?>) declaration).getConstructors().stream() : Stream.<CtConstructor<?>>of())).forEach(SpoonClassParser.nonImplicit((CtExecutable<?> method) ->
                    children.add(new JMethod() {
                        final boolean isCtor = method instanceof CtConstructor<?>;
                        final String name = (isCtor ? "<init>" : method.getSimpleName());
                        final String desc = name + JVMSignatureBuilder.getJvmMethodSignature(method);
                        @Override
                        public String getDescriptor() {
                            return desc;
                        }

                        @Override
                        public boolean isConstructor() {
                            return isCtor;
                        }

                        private int linePos = -1;
                        @Override
                        public OptionalInt getSourceLine() {
                            if (linePos == -1) {
                                linePos = searchLineNumber(method.getPosition().getCompilationUnit(), method.getPosition().getSourceStart());
                            }
                            return OptionalInt.of(linePos);
                        }

                        @Override
                        public String getName() {
                            return name;
                        }

                        @Override
                        public List<JParameter> getParameters() {
                            return method.getParameters().stream()
                                    .map(it -> (JParameter) it::getSimpleName)
                                    .collect(Collectors.toList());
                        }

                        @Override
                        public List<JParameter> getTypeParameters() {
                            return ((CtFormalTypeDeclarer)method).getFormalCtTypeParameters().stream()
                                    .map(it -> (JParameter) it::getSimpleName)
                                    .collect(Collectors.toList());
                        }
                    })));
            declaration.getNestedTypes().forEach(type -> children.add(createClass(type)));
        }

        @Override
        public String getFullyQualifiedName() {
            return declaration.getQualifiedName();
        }

        @Override
        public List<JElement> getChildren() {
            return children;
        }

        private int linePos = -1;
        @Override
        public OptionalInt getSourceLine() {
            if (linePos == -1) {
                linePos = searchLineNumber(declaration.getPosition().getCompilationUnit(), declaration.getPosition().getSourceStart());
            }
            return OptionalInt.of(linePos);
        }

        @Override
        public String getName() {
            return declaration.getSimpleName();
        }

        @Override
        public List<JParameter> getTypeParameters() {
            return declaration.getFormalCtTypeParameters().stream()
                    .map(it -> (JParameter) it::getSimpleName)
                    .collect(Collectors.toList());
        }
    }

    private static int searchLineNumber(CtCompilationUnit compilationUnit, int position) {
        int[] lineSeparatorPositions = getLineSeparatorPositions(compilationUnit);
        if (lineSeparatorPositions == null) {
            return 1;
        }
        int length = lineSeparatorPositions.length;
        if (length == 0) {
            return 1;
        }
        int g = 0;
        int d = length - 1;
        int m = 0;
        int start;
        while (g <= d) {
            m = (g + d) / 2;
            if (position < (start = lineSeparatorPositions[m])) {
                d = m - 1;
            } else if (position > start) {
                g = m + 1;
            } else {
                return m + 1;
            }
        }
        if (position < lineSeparatorPositions[m]) {
            return m + 1;
        }
        return m + 2;
    }

    private static int[] getLineSeparatorPositions(CtCompilationUnit compilationUnit) {
        return compilationUnit == null ? null : compilationUnit.getLineSeparatorPositions();
    }

    private static <T extends CtElement> Consumer<T> nonImplicit(Consumer<T> consumer) {
        return it -> {
            if (!it.isImplicit()) {
                consumer.accept(it);
            }
        };
    }
}
