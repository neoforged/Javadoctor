package net.neoforged.javadoctor.injector.jbpsi;

import net.neoforged.javadoctor.injector.ClassParserFactory;
import net.neoforged.javadoctor.injector.ast.JClassParser;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class JBPsiFactory implements ClassParserFactory {
    @Override
    public String getName() {
        return "jb-psi";
    }

    @Override
    public JClassParser createParser(Collection<File> classpath, File input, int javaVersion) throws IOException {
        if (Integer.parseInt(System.getProperty("java.version").split("\\.")[0]) < javaVersion) {
            throw new IllegalArgumentException("Unsupported java version!");
        }
        return new JBPsiParser(classpath, input);
    }
}
