package net.neoforged.javadoctor.injector;

import com.github.javaparser.ParseResult;
import com.google.gson.JsonObject;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.neoforged.javadoctor.io.gson.GsonJDocIO;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {
    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputO = parser.accepts("input", "Input jar file").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output", "Output jar file").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> jsonO = parser.accepts("doctor", "The Javadoctor json file(s)").withRequiredArg().ofType(File.class);
        OptionSpec<File> classpathO = parser.accepts("classpath", "The classpath to use when resolving classes").withRequiredArg().ofType(File.class);
        OptionSpec<Integer> javaVersion = parser.accepts("java-version", "The version of Java to use for parsing").withRequiredArg().ofType(Integer.class).required();
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println();
            parser.printHelpOn(System.err);
            System.exit(1);
            return;
        }

        final List<JavadocProvider> providers = new ArrayList<>();
        try (final FileSystem in = FileSystems.newFileSystem(options.valueOf(inputO).toPath(), (ClassLoader) null)) {
            final Path javadoctorJson = in.getPath("javadoctor.json");
            if (Files.exists(javadoctorJson)) {
                try (final Reader is = Files.newBufferedReader(javadoctorJson)) {
                    providers.add(GsonJDocIO.read(GsonJDocIO.GSON, GsonJDocIO.GSON.fromJson(is, JsonObject.class)).getClassDocs()::get);
                }
            }
        }

        final List<File> doctors = options.valuesOf(jsonO);
        if (providers.isEmpty() && doctors.isEmpty()) {
            System.err.println("No doctor files have been specified and none could be found in the input jar!");
            System.exit(1);
        }

        for (final File doctor : doctors) {
            try (final Reader is = new FileReader(doctor)) {
                providers.add(GsonJDocIO.read(GsonJDocIO.GSON, GsonJDocIO.GSON.fromJson(is, JsonObject.class)).getClassDocs()::get);
            }
        }

        final JavadocInjector injector = new JavadocInjector(
                options.valueOf(javaVersion), new CombiningJavadocProvider(providers),
                options.valuesOf(classpathO).stream().map(File::toPath).collect(Collectors.toSet())
        );

        final Path out = options.valueOf(outputO).toPath();
        Files.createDirectories(out.getParent());

        try (final ZipInputStream input = new ZipInputStream(new FileInputStream(options.valueOf(inputO)));
             final ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(out))) {
            ZipEntry next;
            while ((next = input.getNextEntry()) != null) {
                final ZipEntry newEntry = new ZipEntry(next);
                if (next.getName().endsWith(".java")) {
                    final byte[] bytes = readAllBytes(input);
                    final ParseResult<JavadocInjector.InjectionResult> result = injector.injectDocs(
                            new String(bytes, StandardCharsets.UTF_8),
                            getMappings(next)
                    );

                    if (result.isSuccessful()) {
                        final JavadocInjector.InjectionResult res = result.getResult().get();
                        if (res.mapping != null) {
                            newEntry.setExtra(getCodeLineData(res.mapping));
                        }
                        output.putNextEntry(newEntry);
                        output.write(res.newSource.getBytes(StandardCharsets.UTF_8));
                    } else {
                        System.err.println("Failed to read or process class " + next.getName() + ": ");
                        result.getProblems().forEach(System.err::println);

                        output.putNextEntry(newEntry);
                        output.write(bytes);
                    }
                } else {
                    output.putNextEntry(newEntry);
                    if (!newEntry.isDirectory()) {
                        copy(input, output);
                    }
                }
                output.closeEntry();
            }
        }
    }

    private static void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    @Nullable
    private static int[] getMappings(ZipEntry entry) throws IOException {
        if (entry.getExtra() == null || entry.getExtra().length < 5) return null;
        final ByteBuffer buf = ByteBuffer.wrap(entry.getExtra());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        if (buf.getShort() != 0x4646) return null;
        final int length = (buf.getShort() - 1) / 2;
        final byte version = buf.get();
        if (version != 1) {
            throw new IllegalArgumentException("Unknown mapping file version: " + version + ". entry: " + entry.getName());
        }
        final int[] mapping = new int[length];
        for (int i = 0; i < length; i++) {
            mapping[i] = buf.getShort();
        }
        return mapping;
    }

    private static byte[] getCodeLineData(int[] mappings) {
        if (mappings == null || mappings.length == 0) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(5 + (mappings.length * 2));
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // Zip Extra entry header, described in http://www.info-zip.org/doc/appnote-19970311-iz.zip
        buf.putShort((short)0x4646); //FF - ForgeFlower
        buf.putShort((short)((mappings.length * 2) + 1)); // Mapping data + our version marker
        buf.put((byte)1); // Version code, in case we want to change it in the future.
        for (int line : mappings) {
            buf.putShort((short)line);
        }
        return buf.array();
    }
}
