package net.neoforged.javadoctor.io.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.DocReferences;
import net.neoforged.javadoctor.spec.JavadocEntry;
import net.neoforged.javadoctor.spec.JavadoctorInformation;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GsonJDocIO {
    private static final Type M_S_E = new com.google.gson.reflect.TypeToken<Map<String, JavadocEntry>>() {}.getType();
    private static final Type M_S_J = new com.google.gson.reflect.TypeToken<Map<String, ClassJavadoc>>() {}.getType();
    private static final Type M_S_LS = new com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.getType();
    public static final JsonDeserializer<ClassJavadoc> JAVADOC_READER = (json, typeOfT, context) -> {
        if (!json.isJsonObject())
            throw new JsonIOException("Expected json object but found " + json);
        final JsonObject obj = json.getAsJsonObject();

        JavadocEntry jdoc = null;
        if (obj.has("clazz")) {
            jdoc = context.deserialize(obj.get("clazz"), JavadocEntry.class);
        }

        Map<String, JavadocEntry> fields = null;
        if (obj.has("fields")) {
            fields = context.deserialize(obj.get("fields"), M_S_E);
        }

        Map<String, JavadocEntry> methods = null;
        if (obj.has("methods")) {
            methods = context.deserialize(obj.get("methods"), M_S_E);
        }

        Map<String, ClassJavadoc> innerClasses = null;
        if (obj.has("innerClasses")) {
            innerClasses = context.deserialize(obj.get("innerClasses"), M_S_J);
        }
        return new ClassJavadoc(jdoc, methods, fields, innerClasses);
    };

    public static final JsonDeserializer<JavadocEntry> ENTRY_READER = (json, typeOfT, context) -> {
        if (!json.isJsonObject())
            throw new JsonIOException("Expected json object but found " + json);
        final JsonObject obj = json.getAsJsonObject();

        String jdoc = null;
        if (obj.has("doc")) {
            jdoc = obj.get("doc").getAsString();
        }

        Map<String, List<String>> tags = null;
        if (obj.has("tags")) {
            tags = context.deserialize(obj.get("tags"), M_S_LS);
        }

        String[] parameters = null;
        if (obj.has("parameters")) {
            parameters = context.deserialize(obj.get("parameters"), String[].class);
        }

        String[] typeParameters = null;
        if (obj.has("typeParameters")) {
            typeParameters = context.deserialize(obj.get("typeParameters"), String[].class);
        }
        return new JavadocEntry(jdoc, tags, parameters, typeParameters);
    };

    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(ClassJavadoc.class, JAVADOC_READER)
            .registerTypeAdapter(JavadocEntry.class, ENTRY_READER)
            .create();

    public static JavadoctorInformation read(Gson gson, JsonObject object) {
        if (object.has("javadoctorSpec")) {
            final int spec = object.get("javadoctorSpec").getAsInt();
            object.remove("javadoctorSpec");
            if (spec == 1) {
                return new JavadoctorInformation(
                        new DocReferences(new HashMap<>()),
                        gson.fromJson(object, new TypeToken<Map<String, ClassJavadoc>>() {})
                );
            } else if (spec == 2) {
                return gson.fromJson(object, JavadoctorInformation.class);
            } else {
                throw new UnsupportedOperationException("Cannot read javadocs of spec: " + spec);
            }
        }
        return gson.fromJson(object, JavadoctorInformation.class);
    }

    public static JsonObject write(Gson gson, JavadoctorInformation docs) {
        final JsonObject object = gson.toJsonTree(docs).getAsJsonObject();
        object.addProperty("javadoctorSpec", 2);
        return object;
    }
}
