package net.neoforged.javadoctor.collector;

import net.neoforged.javadoctor.collector.util.Hierarchy;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DocFQNExpander {
    public static final Pattern PATTERN = Pattern.compile("@(?<tag>link|linkplain|see|value)(?<space>\\s+)(?<owner>[\\w$.]*)(?:#(?<member>[\\w%]+)?(\\((?<desc>[\\w$., \\[\\]]+)?\\))?)?");
    public static String expand(TypeElement declaringClass, String doc, JavadocCollector.Imports imports) {
        final TypeElement topLevel = Hierarchy.getTopLevel(declaringClass);
        final Supplier<Map<String, TypeElement>> members = JavadocCollector.memoized(() -> {
            final Map<String, TypeElement> memberMap = new HashMap<>();
            Stream.concat(Stream.of(topLevel), Hierarchy.walkChildren(topLevel))
                    .forEach(t -> t.getEnclosedElements().stream().filter(f -> f.getKind() == ElementKind.ENUM_CONSTANT || f.getKind() == ElementKind.FIELD || f.getKind() == ElementKind.METHOD)
                            .forEach(element -> memberMap.put(
                                    element.getKind() == ElementKind.METHOD ? element.getSimpleName().toString() : "#" + element.getSimpleName(),
                                    t
                            )));
            return memberMap;
        });
        return PATTERN.matcher(doc).replaceAll(result -> {
            final StringBuffer text = new StringBuffer()
                    .append('@').append(result.group(1)).append(result.group(2));
            final String owner = imports.getQualified(result.group(3));
            final String member = result.group(4);
            final String desc = result.group(6);
            if (owner == null || owner.isBlank()) {
                if (member == null) {
                    return result.group(0);
                }
                final TypeElement actualOwner = members.get().get(desc == null ? "#" + member : member); // this isn't flawless, it will find the wrong one when there's a lot of nesting of classes with the same member names
                if (actualOwner != null) {
                    text.append(actualOwner.getQualifiedName());
                }
            } else {
                text.append(owner);
            }
            if (member == null) {
                return text.toString();
            }
            text.append('#').append(member);
            if (desc == null) {
                return text.toString();
            }
            return text.append('(').append(String.join(", ", getParameterTypes(desc, imports))).append(')').toString();
        });
    }

    private static String[] getParameterTypes(String desc, JavadocCollector.Imports imports) {
        final String[] sDesc = desc.split(",");
        final String[] nDesc = new String[sDesc.length];
        for (int i = 0; i < sDesc.length; i++) {
            String d = sDesc[i].trim();
            if (d.endsWith("...")) {
                nDesc[i] = imports.getQualified(d.substring(0, d.length() - 3)) + "...";
            } else {
                int arrayAmount = 0;
                while (d.endsWith("[]")) {
                    arrayAmount++;
                    d = d.substring(0, d.length() - 2);
                }
                nDesc[i] = imports.getQualified(d) + "[]".repeat(arrayAmount);
            }
        }
        return nDesc;
    }
}
