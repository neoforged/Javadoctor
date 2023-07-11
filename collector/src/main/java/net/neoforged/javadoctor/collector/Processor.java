package net.neoforged.javadoctor.collector;

import com.sun.source.util.Trees;
import net.neoforged.javadoctor.io.gson.GsonJDocIO;
import net.neoforged.javadoctor.spec.DocReferences;
import net.neoforged.javadoctor.spec.JavadoctorInformation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.Writer;
import java.util.Set;
import java.util.function.Predicate;

@SupportedAnnotationTypes("*")
@SupportedOptions("collectionPackages")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Processor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;
        final String[] toCollect = processingEnv.getOptions().get("collectionPackages").split(",");
        final Predicate<String> isCollectible = s -> {
            for (final String c : toCollect) {
                if (s.startsWith(c + ".") || s.equals(c)) {
                    return true;
                }
            }
            return false;
        };

        final JavadocCollector collector = new JavadocCollector(processingEnv.getTypeUtils(), processingEnv.getMessager(), processingEnv.getElementUtils(), Trees.instance(processingEnv));

        processingEnv.getElementUtils().getAllModuleElements()
                .stream().flatMap(el -> el.getEnclosedElements().stream().map(PackageElement.class::cast))
                .filter(pkg -> isCollectible.test(pkg.getQualifiedName().toString()))
                .flatMap(pkg -> pkg.getEnclosedElements().stream().map(TypeElement.class::cast))
                .distinct()
                .forEach(collector::collect);

        if (Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("mixinCollect", "true"))) {
            final MixinTypes types = new MixinTypes(processingEnv.getTypeUtils(), processingEnv.getElementUtils());
            roundEnv.getElementsAnnotatedWith(types.Mixin).stream()
                    .filter(el -> el.getKind() == ElementKind.CLASS)
                    .map(TypeElement.class::cast)
                    .forEach(type -> collector.collectMixin(type, types));
        }

        try (final Writer writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "javadoctor.json")
                .openWriter()) {
            GsonJDocIO.GSON.toJson(GsonJDocIO.write(GsonJDocIO.GSON, new JavadoctorInformation(new DocReferences(collector.internalClassNames), collector.javadocs)), writer);
        } catch (Exception exception) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not write javadocs json: " + exception);
        }

        return false;
    }

}
