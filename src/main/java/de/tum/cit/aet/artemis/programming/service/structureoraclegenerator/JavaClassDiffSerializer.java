package de.tum.cit.aet.artemis.programming.service.structureoraclegenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.thoughtworks.qdox.model.JavaAnnotatedElement;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaConstructor;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;

import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.StructureOracleEntry.OracleAttribute;
import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.StructureOracleEntry.OracleClass;
import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.StructureOracleEntry.OracleConstructor;
import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.StructureOracleEntry.OracleMethod;

/**
 * This class is used to serialize the elements that are generally defined in types, e.g. methods and various properties of the types. These properties are defined as the hierarchy
 * of the types.
 */
record JavaClassDiffSerializer(JavaClassDiff javaClassDiff) {

    /**
     * This method is used to serialize the class properties of each type defined in the types diff into a typed oracle class containing the following information:
     * - Name of the type
     * - Package of the type
     * - Interface stereotype
     * - Enum stereotype
     * - Abstract stereotype
     * - Superclass (if any)
     * - Super interfaces (if any)
     * - Annotations (if any)
     *
     * @return The oracle class consisting of the wanted hierarchy properties of a type defined in the types diff.
     */
    OracleClass serializeClassProperties() {
        // Adding superclass name, interfaces and annotations only if present
        String superclass = javaClassDiff.superClassNameDiff.isEmpty() ? null : javaClassDiff.superClassNameDiff;
        List<String> interfaces = javaClassDiff.superInterfacesDiff.isEmpty() ? null : serializeSuperInterfaces(javaClassDiff.superInterfacesDiff);
        List<String> annotations = javaClassDiff.annotationsDiff.isEmpty() ? null : serializeAnnotations(javaClassDiff.annotationsDiff);

        return new OracleClass(javaClassDiff.getName(), javaClassDiff.getPackageName(), javaClassDiff.isInterfaceDifferent, javaClassDiff.isEnumDifferent,
                javaClassDiff.isAbstractDifferent, superclass, interfaces, annotations);
    }

    private List<String> serializeSuperInterfaces(Iterable<JavaClass> superInterfaces) {
        List<String> superInterfaceNames = new ArrayList<>();
        superInterfaces.forEach(superInterface -> superInterfaceNames.add(superInterface.getSimpleName()));
        return superInterfaceNames;
    }

    private List<String> serializeAnnotations(Iterable<JavaAnnotation> annotations) {
        List<String> annotationNames = new ArrayList<>();
        annotations.forEach(annotation -> annotationNames.add(annotation.getType().getSimpleName()));
        return annotationNames;
    }

    /**
     * This method is used to serialize the attributes of a class into a list containing the following information for each attribute defined in the classes diff:
     * - Name - Modifiers (if any) - Type
     *
     * @return The list consisting of the oracle representation for each attribute defined in the classes diff.
     */
    List<OracleAttribute> serializeAttributes() {
        List<OracleAttribute> attributes = new ArrayList<>();

        javaClassDiff.attributesDiff.stream().filter(attribute -> !JavaClassDiffSerializer.isElementToIgnore(attribute))
                .forEach(attribute -> attributes.add(createAttribute(attribute)));

        return attributes;
    }

    private OracleAttribute createAttribute(JavaField attribute) {
        List<String> modifiers = SerializerUtil.serializeModifiers(new HashSet<>(attribute.getModifiers()), attribute);
        List<String> annotations = SerializerUtil.serializeAnnotationsIfPresent(attribute.getAnnotations());
        return new OracleAttribute(attribute.getName(), modifiers, annotations, attribute.getType().getValue());
    }

    /**
     * This method is used to serialize the enums of a class into a list containing each enum value:
     *
     * @return The list consisting of the name of each enum value defined in the classes diff.
     */
    List<String> serializeEnums() {
        List<String> enums = new ArrayList<>();

        javaClassDiff.enumsDiff.stream().filter(enumField -> !isElementToIgnore(enumField)).forEach(enumField -> enums.add(enumField.getName()));

        return enums;
    }

    /**
     * This method is used to serialize the constructors of a class into a list containing the following information for each constructor defined in the classes diff:
     * - Modifiers (if any) - Parameter types (if any)
     *
     * @return The list consisting of the oracle representation for each constructor defined in the classes diff.
     */
    List<OracleConstructor> serializeConstructors() {
        List<OracleConstructor> constructors = new ArrayList<>();

        javaClassDiff.constructorsDiff.stream().filter(constructor -> !isElementToIgnore(constructor)).forEach(constructor -> constructors.add(serializeConstructor(constructor)));

        return constructors;
    }

    private OracleConstructor serializeConstructor(JavaConstructor constructor) {
        // No need to check for isEmpty before adding; let the serializer utility methods decide how to handle empty collections
        return new OracleConstructor(SerializerUtil.serializeModifiers(new HashSet<>(constructor.getModifiers()), constructor),
                SerializerUtil.serializeParameters(constructor.getParameters()), SerializerUtil.serializeAnnotations(constructor.getAnnotations()));
    }

    /**
     * This method is used to serialize the methods of a type into a list containing the following information for each method defined in the types diff:
     * - Name - Modifiers (if any) - Parameter types (if any) - Return type
     *
     * @return The list consisting of the oracle representation for each method defined in the types diff.
     */
    List<OracleMethod> serializeMethods() {
        List<OracleMethod> methods = new ArrayList<>();

        javaClassDiff.methodsDiff.stream().filter(method -> !isElementToIgnore(method)).forEach(method -> methods.add(createMethod(method)));

        return methods;
    }

    private OracleMethod createMethod(JavaMethod method) {
        List<String> modifiers = SerializerUtil.serializeModifiers(new HashSet<>(method.getModifiers()), method);
        List<String> annotations = SerializerUtil.serializeAnnotationsIfPresent(method.getAnnotations());

        // No need to check for isEmpty before adding; let the serializer utility methods decide how to handle empty collections
        return new OracleMethod(method.getName(), modifiers, annotations, SerializerUtil.serializeParameters(method.getParameters()), method.getReturnType().getValue());
    }

    public static boolean isElementToIgnore(JavaAnnotatedElement element) {
        return element.getTagByName("oracleIgnore") != null;
    }
}
