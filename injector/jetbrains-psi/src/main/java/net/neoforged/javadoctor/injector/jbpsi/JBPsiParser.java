package net.neoforged.javadoctor.injector.jbpsi;

import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.JavaCoreApplicationEnvironment;
import com.intellij.core.JavaCoreProjectEnvironment;
import com.intellij.lang.jvm.JvmNamedElement;
import com.intellij.lang.jvm.facade.JvmElementProvider;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiElementFinderImpl;
import com.intellij.psi.impl.PsiNameHelperImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.intellij.psi.impl.source.tree.JavaTreeGenerator;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.JavaClassSupers;
import net.neoforged.javadoctor.injector.Result;
import net.neoforged.javadoctor.injector.ast.JClass;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import net.neoforged.javadoctor.injector.ast.JElement;
import net.neoforged.javadoctor.injector.ast.JField;
import net.neoforged.javadoctor.injector.ast.JMethod;
import net.neoforged.javadoctor.injector.ast.JParameter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JBPsiParser implements JClassParser {
    private final PsiManager manager;
    private final VirtualFile sourcesRoot;

    public JBPsiParser(final Collection<File> cp, final File input) throws IOException {


        final Path tempDir = Files.createTempDirectory("javadoctorjbpsi");
        final Disposable rootDisposable = Disposer.newDisposable();
        System.setProperty("idea.home.path", tempDir.toAbsolutePath().toString());

        PathManager.setExplicitConfigPath(tempDir.toAbsolutePath().toString());
        Registry.markAsLoaded();


        final JavaCoreApplicationEnvironment appEnv = new JavaCoreApplicationEnvironment(rootDisposable) {
            @Override
            protected VirtualFileSystem createJrtFileSystem() {
                return new CoreJrtFileSystem();
            }
        };
        final JavaCoreProjectEnvironment javaEnv = new JavaCoreProjectEnvironment(rootDisposable, appEnv);

        cp.forEach(javaEnv::addJarToClassPath);

        addJdkModules(Path.of(System.getProperty("java.home")), javaEnv);

        appEnv.registerApplicationService(JavaClassSupers.class, new com.intellij.psi.impl.JavaClassSupersImpl());
        appEnv.registerApplicationService(InternalPersistentJavaLanguageLevelReaderService.class, new InternalPersistentJavaLanguageLevelReaderService.DefaultImpl());

        // Global extensions
        final ExtensionsAreaImpl appExtensions = appEnv.getApplication().getExtensionArea();
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, PsiAugmentProvider.EP_NAME, PsiAugmentProvider.class);
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, JavaModuleSystem.EP_NAME, JavaModuleSystem.class);
        CoreApplicationEnvironment.registerExtensionPoint(appExtensions, TreeGenerator.EP_NAME, TreeGenerator.class);
        appExtensions.getExtensionPoint(TreeGenerator.EP_NAME).registerExtension(new JavaTreeGenerator(), rootDisposable);

        final MockProject project = javaEnv.getProject();

        // Project extensions
        project.registerService(PsiNameHelper.class, PsiNameHelperImpl.class);

        final ExtensionsAreaImpl projectExtensions = project.getExtensionArea();
        CoreApplicationEnvironment.registerExtensionPoint(projectExtensions, PsiTreeChangePreprocessor.EP.getName(), PsiTreeChangePreprocessor.class);
        CoreApplicationEnvironment.registerExtensionPoint(projectExtensions, PsiElementFinder.EP.getName(), PsiElementFinder.class);
        CoreApplicationEnvironment.registerExtensionPoint(projectExtensions, JvmElementProvider.EP_NAME, JvmElementProvider.class);
        PsiElementFinder.EP.getPoint(project).registerExtension(new PsiElementFinderImpl(project), rootDisposable);

        this.manager = PsiManager.getInstance(project);

        this.sourcesRoot = javaEnv.getEnvironment().getJarFileSystem().findFileByPath(input + "!/");
    }

    public static void addJdkModules(Path jdkHome, JavaCoreProjectEnvironment javaEnv) {
        // We run with J17, every normal JDK should have these paths

        final var jrtFileSystem = javaEnv.getEnvironment().getJrtFileSystem();
        assert jrtFileSystem != null;

        final var jdkVfsRoot = jrtFileSystem.findFileByPath(".");
        assert jdkVfsRoot != null;

        final var modulesFolder = jdkVfsRoot.findChild("modules");
        assert modulesFolder != null;

        List<String> modules = readModulesFromReleaseFile(jdkHome);
        if (modules != null) {
            modules.stream().map(modulesFolder::findChild)
                    .peek(Objects::requireNonNull)
                    .forEach(javaEnv::addSourcesToClasspath);
        } else {
            Stream.of(modulesFolder.getChildren())
                    .filter(VirtualFile::isDirectory)
                    .forEach(javaEnv::addSourcesToClasspath);
        }
    }

    private static List<String> readModulesFromReleaseFile(Path jrtBaseDir) {
        try (final InputStream stream = Files.newInputStream(jrtBaseDir.resolve("release"))) {
            final Properties p = new Properties();
            p.load(stream);
            final String modules = p.getProperty("MODULES");
            if (modules != null) {
                return StringUtil.split(StringUtil.unquoteString(modules), " ");
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    @Override
    public Result<List<JClass>> parseFromPath(String path, String text) {
        final var relative = sourcesRoot.findFileByRelativePath(path);
        if (relative == null) {
            return new Result<>(List.of("Relative path not found"));
        }

        final PsiJavaFile file = (PsiJavaFile) manager.findFile(relative);
        if (file == null) {
            return new Result<>(List.of("No java file was found at that path"));
        }

        var lsPos = IntStream.builder();
        var from = -1;
        while ((from = text.indexOf('\n', from + 1)) != -1) {
            lsPos.add(from);
        }
        var lineSeparatorPositions = lsPos.build().toArray();

        final UnaryOperator<Integer> pos = position -> {
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
        };

        return new Result<>(Stream.of(file.getClasses())
                .map(clz -> createClass(pos, clz))
                .toList());
    }

    public static JClass createClass(UnaryOperator<Integer> pos, PsiClass psiClass) {
        return psiClass.isRecord() ? new PsJClass.Rec(pos, psiClass) : new PsJClass(pos, psiClass);
    }

    public static class PsJClass implements JClass {
        public static class Rec extends PsJClass implements WithParameters {

            public Rec(UnaryOperator<Integer> pos, PsiClass clz) {
                super(pos, clz);
            }

            @Override
            public List<JParameter> getParameters() {
                return Stream.of(clz.getRecordComponents())
                        .map(e -> (JParameter) e::getName)
                        .toList();
            }

            @Override
            public ArrayList<JElement> getChildren() {
                return super.getChildren();
            }
        }

        private final UnaryOperator<Integer> pos;
        protected final PsiClass clz;

        public PsJClass(UnaryOperator<Integer> pos, PsiClass clz) {
            this.pos = pos;
            this.clz = clz;
            this.sourceLine = new CachedPosition(clz);
        }

        @Override
        public String getFullyQualifiedName() {
            return clz.getQualifiedName();
        }

        @Override
        public ArrayList<JElement> getChildren() {
            final var children = new ArrayList<JElement>();
            for (final var field : clz.getFields()) {
                children.add(new JField() {
                    private final CachedPosition sourceLine = new CachedPosition(field.getModifierList());

                    @Override
                    public OptionalInt getSourceLine() {
                        return sourceLine.get();
                    }

                    @Override
                    public String getName() {
                        return field.getName();
                    }
                });
            }

            for (final var cls : clz.getInnerClasses()) {
                children.add(createClass(pos, cls));
            }

            for (final var method : clz.getMethods()) {
                children.add(new JMethod() {
                    @Override
                    public String getDescriptor() {
                        return getName() + getSignature(method);
                    }

                    @Override
                    public boolean isConstructor() {
                        return method.isConstructor();
                    }

                    private final CachedPosition sourceLine = new CachedPosition(method.getModifierList());

                    @Override
                    public OptionalInt getSourceLine() {
                        return sourceLine.get();
                    }

                    @Override
                    public String getName() {
                        return method.isConstructor() ? "<init>" : method.getName();
                    }

                    @Override
                    public List<JParameter> getParameters() {
                        return Stream.of(method.getParameterList().getParameters())
                                .map(Param::new)
                                .collect(Collectors.toList());
                    }

                    @Override
                    public List<JParameter> getTypeParameters() {
                        return Stream.of(method.getTypeParameters())
                                .map(Param::new)
                                .collect(Collectors.toList());
                    }
                });
            }
            return children;
        }

        private final CachedPosition sourceLine;

        @Override
        public OptionalInt getSourceLine() {
            return sourceLine.get();
        }

        @Override
        public String getName() {
            return clz.getName();
        }

        @Override
        public List<JParameter> getTypeParameters() {
            return Stream.of(clz.getTypeParameters())
                    .map(Param::new)
                    .collect(Collectors.toList());
        }

        public class CachedPosition {
            private OptionalInt pos = OptionalInt.empty();
            private final PsiElement element;

            public CachedPosition(PsiElement element) {
                this.element = element;
            }

            public OptionalInt get() {
                if (pos.isEmpty()) {
                    pos = OptionalInt.of(PsJClass.this.pos.apply(element.getTextOffset()));
                }
                return pos;
            }
        }
    }

    public record Param(JvmNamedElement item) implements JParameter {
        @Override
        public String getName() {
            return item.getName();
        }
    }

    public static String getSignature(PsiMethod method) {
        final StringBuilder signature = new StringBuilder();
        signature.append("(");

        for (final var param : method.getParameterList().getParameters()) {
            signature.append(ClassUtil.getBinaryPresentation(param.getType()));
        }

        signature.append(")");
        signature.append(Optional.ofNullable(method.getReturnType())
                .map(ClassUtil::getBinaryPresentation).orElse("V"));
        return signature.toString();
    }

}
