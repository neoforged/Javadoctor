package net.neoforged.javadoctor.injector.spoon;

import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.TypeFactory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JVMSignatureBuilder {

    public static String getJvmMethodSignature(CtExecutable<?> method) {
        String argTypes = method.getParameters().stream()
                .map(CtParameter::getType)
                .map(JVMSignatureBuilder::getJvmTypeSignature)
                .collect(Collectors.joining());
        // Methods are void in bytecode
        String returnType = method instanceof CtConstructor<?> ? "V" : getJvmTypeSignature(method.getType());

        return String.format("(%s)%s", argTypes, returnType);
    }

    public static String getJvmTypeSignature(CtTypeReference<?> typeRef) {
        final TypeFactory typeFactory = typeRef.getFactory().Type();
        final Map<CtTypeReference<?>, String> primitiveToSignature = new HashMap<>();
        primitiveToSignature.put(typeFactory.booleanPrimitiveType(), "Z");
        primitiveToSignature.put(typeFactory.bytePrimitiveType(), "B");
        primitiveToSignature.put(typeFactory.characterPrimitiveType(), "C");
        primitiveToSignature.put(typeFactory.shortPrimitiveType(), "S");
        primitiveToSignature.put(typeFactory.integerPrimitiveType(), "I");
        primitiveToSignature.put(typeFactory.longPrimitiveType(), "L");
        primitiveToSignature.put(typeFactory.floatPrimitiveType(), "F");
        primitiveToSignature.put(typeFactory.doublePrimitiveType(), "D");
        primitiveToSignature.put(typeFactory.voidPrimitiveType(), "V");

        if (typeRef.isPrimitive()) {
            return primitiveToSignature.get(typeRef);
        } else if (typeRef.isArray()) {
            CtArrayTypeReference<?> arrayTypeRef = (CtArrayTypeReference<?>) typeRef;
            String brackets = IntStream.range(0, arrayTypeRef.getDimensionCount())
                    .mapToObj(i -> "[")
                    .collect(Collectors.joining());
            String elementTypeSignature = getJvmTypeSignature(arrayTypeRef.getArrayType());
            return brackets + elementTypeSignature;
        } else {
            return String.format("L%s;", typeRef.getQualifiedName().replace(".", "/"));
        }
    }
}
