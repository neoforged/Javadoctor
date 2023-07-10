package net.neoforged.javadoctor.collector;

import net.neoforged.javadoctor.collector.util.AnnotationUtils;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class MixinTypes {
    private final Types types;
    public final TypeElement Mixin;
    public final TypeElement Shadow;
    public final TypeElement Unique;
    public MixinTypes(Types types, Elements elements) {
        this.types = types;
        Mixin = elements.getTypeElement("org.spongepowered.asm.mixin.Mixin");
        Shadow = elements.getTypeElement("org.spongepowered.asm.mixin.Shadow");
        Unique = elements.getTypeElement("org.spongepowered.asm.mixin.Unique");
    }

    @Nullable
    public AnnotationUtils getAnnotation(Element element, TypeElement annotation) {
        return element.getAnnotationMirrors().stream()
                .filter(mir -> types.asElement(mir.getAnnotationType()).equals(annotation))
                .findFirst().map(mir -> new AnnotationUtils(types, mir))
                .orElse(null);
    }
}
