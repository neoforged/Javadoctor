package net.neoforged.javadoctor.injector;

import net.neoforged.javadoctor.injector.ast.JClassParser;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public interface ClassParserFactory {
    String getName();
    JClassParser createParser(Collection<File> classpath, File input, int javaVersion) throws IOException;
}
