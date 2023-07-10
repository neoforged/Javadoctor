package net.neoforged.javadoctor.io.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.neoforged.javadoctor.spec.ClassJavadoc;

import java.util.Map;

public class GsonJDocIO {
    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().create();
    public static Map<String, ClassJavadoc> read(Gson gson, JsonObject object) {
        if (object.has("javadoctorSpec")) {
            final int spec = object.get("javadoctorSpec").getAsInt();
            if (spec != 1) {
                throw new UnsupportedOperationException("Cannot read javadocs of spec: " + spec);
            }
        }
        return gson.fromJson(object, new TypeToken<Map<String, ClassJavadoc>>() {});
    }

    public static JsonObject write(Gson gson, Map<String, ClassJavadoc> docs) {
        final JsonObject jsonObject = gson.toJsonTree(docs).getAsJsonObject();
        jsonObject.addProperty("javadoctorSpec", 1);
        return jsonObject;
    }
}
