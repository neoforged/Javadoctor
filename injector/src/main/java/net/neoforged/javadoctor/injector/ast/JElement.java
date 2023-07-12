package net.neoforged.javadoctor.injector.ast;

import java.util.List;
import java.util.OptionalInt;

public interface JElement {
    OptionalInt getSourceLine();
    String getName();

    interface WithParameters {
        List<JParameter> getParameters();
    }
    interface WithTypeParameters {

        List<JParameter> getTypeParameters();
    }
}
