package net.neoforged.javadoctor.injector.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.TypeSolverBuilder;
import net.neoforged.javadoctor.injector.ClassParserFactory;
import net.neoforged.javadoctor.injector.JavadocProvider;
import net.neoforged.javadoctor.injector.ast.JClassParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class JavaparserParserFactory implements ClassParserFactory {
    @Override
    public String getName() {
        return "javaparser";
    }

    @Override
    public JClassParser createParser(Collection<File> classpath, int javaVersion) throws IOException {
        return new JavaparserClassParser(new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.valueOf("JAVA_" + javaVersion))
                .setSymbolResolver(new SymbolResolverWithRecordSupport(
                        solver(classpath)
                ))));
    }

    private static TypeSolver solver(Iterable<File> paths) throws IOException {
        final TypeSolverBuilder builder = new TypeSolverBuilder()
                .withCurrentJRE();
        for (final File path : paths) {
            builder.withJAR(path);
        }
        return builder.build();
    }
}
