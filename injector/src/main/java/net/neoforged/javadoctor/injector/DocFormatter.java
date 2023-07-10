package net.neoforged.javadoctor.injector;

import net.neoforged.javadoctor.spec.JavadocEntry;

import javax.annotation.Nullable;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class DocFormatter {
    public static final int MAX_PARAM_LENGTH = 80;
    public static final Comparator<String> TAGS_ORDER = FixedOrderComparator.of(
            "author",
            "version",
            "param",
            "return",
            "throws",
            "exception",
            "see",
            "since",
            "serial",
            "deprecated"
    );


    @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
    public static WithLength formatDoc(String indent, JavadocEntry entry, @Nullable List<String> parameters, @Nullable List<String> genericTypes) {
        final String[] linesSplit = entry.doc() == null ? new String[0] : entry.doc().split("\n");
        final List<String> lines = new ArrayList<>(linesSplit.length);
        for (String line : linesSplit) {
            lines.add(line);
        }

        final Map<String, List<String>> tags = entry.tags() instanceof HashMap ? entry.tags() : new HashMap<>(entry.tags() == null ? new HashMap<>() : entry.tags());

        appendJust(lines, tags, "author", "version");

        if (entry.parameters() != null && entry.parameters().length != 0) {
            Objects.requireNonNull(parameters);
            appendParameters(lines, entry.parameters(), parameters::get);
        }

        if (entry.typeParameters() != null && entry.typeParameters().length != 0) {
            Objects.requireNonNull(genericTypes);
            appendParameters(lines, entry.typeParameters(), i -> "<" + genericTypes.get(i) + ">");
        }

        tags.entrySet()
            .stream().sorted(Map.Entry.comparingByKey(TAGS_ORDER))
            .flatMap(e -> e.getValue().stream()
                    .sorted(Comparator.naturalOrder())
                    .map(v -> formatTag(e.getKey(), v)))
            .forEach(lines::add);

        final String joined = lines.stream().map(ln -> indent + " * " + ln).collect(Collectors.joining("\n"));
        return new WithLength(indent + "/**\n" + joined + (joined.isEmpty() ? (indent + " */") : ("\n" + indent + " */")), lines.size() + 2);
    }

    private static void appendJust(List<String> lines, Map<String, List<String>> tags, String... toAdd) {
        final Map<String, List<String>> newTags = new HashMap<>();
        for (final String tag : toAdd) {
            final List<String> n = tags.get(tag);
            if (n != null) {
                newTags.put(tag, n);
                tags.remove(tag);
            }
        }
        newTags.entrySet()
            .stream().sorted(Map.Entry.comparingByKey(TAGS_ORDER))
            .flatMap(e -> e.getValue().stream()
                    .sorted(Comparator.naturalOrder())
                    .map(v -> formatTag(e.getKey(), v)))
            .forEach(lines::add);
    }

    private static String formatTag(String name, String value) {
        final String start = "@" + name;
        final StringBuilder builder = new StringBuilder()
                .append(start).append(' ');
        final String[] split = value.split("\n");
        for (int i = 0; i < split.length; i++) {
            if (i == 0) {
                builder.append(split[i]);
            } else {
                builder.append(repeat(" ", start.length() + 1))
                        .append(split[i]);
                if (i < split.length - 1) {
                    builder.append("\n");
                }
            }
        }
        return builder.toString();
    }

    private static void appendParameters(List<String> lines, String[] parameters, IntFunction<String> nameGetter) {
        int paramsIndentSize = 0;
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] == null) continue;
            paramsIndentSize = Math.max(paramsIndentSize, ("@param " + nameGetter.apply(i)).length());
        }

        final String paramIndent = repeat(" ", paramsIndentSize);
        for (int i = 0; i < parameters.length; i++) {
            final int finalI = i;
            splitIntoMultipleLines(MAX_PARAM_LENGTH - paramsIndentSize, parameters[i], (line, index) -> {
                if (index == 0) {
                    lines.add("@param " + nameGetter.apply(finalI) + " " + line);
                } else {
                    lines.add(paramIndent + line);
                }
            });
            paramsIndentSize = Math.max(paramsIndentSize, ("@param " + nameGetter.apply(i)).length());
        }
    }

    public static void splitIntoMultipleLines(int maxLength, @Nullable String str, BiConsumer<String, Integer> consumer) {
        if (str == null) return;

        final BreakIterator boundary = BreakIterator.getWordInstance(Locale.ENGLISH);
        StringBuilder currentLine = null;
        int amount = 0;

        boundary.setText(str);
        int start = boundary.first();

        for (int end = boundary.next();
             end != BreakIterator.DONE;
             start = end, end = boundary.next()) {
            final String word = str.substring(start, end);
            if (currentLine == null) {
                currentLine = new StringBuilder().append(word);
            } else {
                if (currentLine.length() + word.length() > maxLength) {
                    consumer.accept(currentLine.toString(), amount++);
                    currentLine = new StringBuilder().append(word);
                } else {
                    currentLine.append(word);
                }
            }
        }

        if (currentLine != null) {
            consumer.accept(currentLine.toString(), amount);
        }
    }

    public static final class WithLength {
        public final String doc;
        public final int length;

        public WithLength(String doc, int length) {
            this.doc = doc;
            this.length = length;
        }
    }

    public static String repeat(String string, int amount) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < amount; i++) {
            builder.append(string);
        }
        return builder.toString();
    }
}
