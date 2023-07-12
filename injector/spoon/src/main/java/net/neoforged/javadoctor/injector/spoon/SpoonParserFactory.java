package net.neoforged.javadoctor.injector.spoon;

import net.neoforged.javadoctor.injector.ClassParserFactory;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import spoon.Launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;

public class SpoonParserFactory implements ClassParserFactory {
    @Override
    public String getName() {
        return "spoon";
    }

    @Override
    public JClassParser createParser(Collection<File> classpath, int javaVersion) {
        return new SpoonClassParser(launcher(classpath, javaVersion));
    }

    private static Supplier<Launcher> launcher(Collection<File> paths, int javaVersion) {
        return () -> {
            final Launcher launcher = new Launcher();
            launcher.getEnvironment().setComplianceLevel(javaVersion);
            launcher.getEnvironment().setSourceClasspath(paths.stream().map(File::getAbsolutePath).toArray(String[]::new));
            return launcher;
        };
    }
}
