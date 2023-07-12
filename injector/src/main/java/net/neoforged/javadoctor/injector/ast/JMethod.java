package net.neoforged.javadoctor.injector.ast;

public interface JMethod extends JElement, JElement.WithParameters, JElement.WithTypeParameters {
    String getDescriptor();
    boolean isConstructor();
}
