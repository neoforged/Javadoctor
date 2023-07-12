package net.neoforged.javadoctor.injector.ast;

import java.util.List;

public interface JClass extends JElement, JElement.WithTypeParameters {
    String getFullyQualifiedName();

    List<JElement> getChildren();
}
