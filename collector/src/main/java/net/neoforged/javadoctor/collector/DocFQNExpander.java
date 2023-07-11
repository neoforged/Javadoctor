package net.neoforged.javadoctor.collector;

import net.neoforged.javadoctor.collector.util.Hierarchy;
import net.neoforged.javadoctor.collector.util.Names;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record DocFQNExpander(Elements elements, Names names, Map<String, String> internalClassNames) {
    public static final Pattern PATTERN = Pattern.compile("@(?<tag>link|linkplain|see|value)(?<space>\\s+)(?<owner>[\\w$.]*)(?:#(?<member>[\\w%]+)?(?<descFull>\\((?<desc>[\\w$., \\[\\]]+)?\\))?)?");
    public String expand(TypeElement declaringClass, String doc, JavadocCollector.Imports imports) {
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
            final String owner = getQualified(imports, result.group(3));
            final String member = result.group(4);
            final String descFull = result.group(5);
            final boolean hasDesc = descFull != null && !descFull.isBlank();
            String desc = result.group(6);
            if (hasDesc && desc == null) desc = "";
            if (owner == null || owner.isBlank()) {
                if (member == null) {
                    return result.group(0);
                }
                final TypeElement actualOwner = members.get().get(hasDesc ? member : "#" + member); // this isn't flawless, it will find the wrong one when there's a lot of nesting of classes with the same member names
                if (actualOwner != null) {
                    final String qualifiedName = actualOwner.getQualifiedName().toString();
                    text.append(qualifiedName);
                    internalClassNames.put(qualifiedName, names.getInternalName(actualOwner));
                }
            } else {
                text.append(owner);
            }
            if (member == null) {
                return text.toString();
            }
            text.append('#').append(member);
            if (!hasDesc) {
                return text.toString();
            }
            return text.append('(').append(String.join(", ", getParameterTypes(desc, imports))).append(')').toString();
        });
    }

    private String[] getParameterTypes(String desc, JavadocCollector.Imports imports) {
        final String[] sDesc = desc.split(",");
        final String[] nDesc = new String[sDesc.length];
        for (int i = 0; i < sDesc.length; i++) {
            String d = sDesc[i].trim();
            if (d.endsWith("...")) {
                nDesc[i] = getQualified(imports, d.substring(0, d.length() - 3)) + "...";
            } else {
                int arrayAmount = 0;
                while (d.endsWith("[]")) {
                    arrayAmount++;
                    d = d.substring(0, d.length() - 2);
                }
                nDesc[i] = getQualified(imports, d) + "[]".repeat(arrayAmount);
            }
        }
        return nDesc;
    }

    @Nullable
    private String getQualified(JavadocCollector.Imports imports, @Nullable String reference) {
        if (reference == null) return null;

        final String qualified = imports.getQualified(reference);
        if (!qualified.isBlank()) {
            if (internalClassNames.get(qualified) == null) {
                final var typeEl = elements.getTypeElement(qualified);
                if (typeEl != null) {
                    internalClassNames.put(qualified, names.getInternalName(typeEl));
                }
            }
        }
        return qualified;
    }
}
