package de.tum.cit.aet.artemis.service.util.structureoraclegenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaConstructor;
import com.thoughtworks.qdox.model.JavaExecutable;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaParameter;

/**
 * This class represents the so-called diff of the solution type and the template type. The solution type is a fully defined type found in the solution of a programming exercise.
 * The template type is its counterpart found in the template of the same exercise. It is clear that the template type misses some of the structural elements of the solution type.
 * This diff contains information on these structural elements. The ones the diff currently handles is: - Class name, - Package name, - Interface stereotype, - Enum stereotype, -
 * Abstract modifier, - Superclass name, - Superinterfaces names, - Methods.
 */
public class JavaClassDiff {

    private final JavaClass solutionClass;

    private final JavaClass templateClass;

    private final String name;

    private final String packageName;

    final boolean isInterfaceDifferent;

    final boolean isEnumDifferent;

    final boolean isAbstractDifferent;

    final String superClassNameDiff;

    final List<JavaClass> superInterfacesDiff;

    final List<JavaAnnotation> annotationsDiff;

    final List<JavaField> attributesDiff;

    final List<JavaField> enumsDiff;

    final List<JavaConstructor> constructorsDiff;

    final List<JavaMethod> methodsDiff;

    JavaClassDiff(JavaClass solutionClass, JavaClass templateClass) {
        this.solutionClass = solutionClass;
        this.templateClass = templateClass;
        this.name = generateName();
        this.packageName = generatePackageName();
        this.isInterfaceDifferent = isInterfaceDifferent();
        this.isEnumDifferent = isEnumDifferent();
        this.isAbstractDifferent = isAbstractDifferent();
        this.superClassNameDiff = generateSuperClassName();
        this.superInterfacesDiff = generateSuperInterfaces();
        this.annotationsDiff = generateAnnotationsDiff();
        this.attributesDiff = generateAttributesDiff();
        this.enumsDiff = generateEnumsDiff();
        this.constructorsDiff = generateConstructorsDiff();
        this.methodsDiff = generateMethodsDiff();
    }

    public String getName() {
        return name;
    }

    public String getPackageName() {
        return packageName;
    }

    /**
     * This method gets the type name of both the solution and the template type. We do not talk about the diff here, since both the solution and the template type are supposed to
     * have the same name.
     *
     * @return The name of the solution and template type.
     */
    private String generateName() {
        return solutionClass.getSimpleName();
    }

    /**
     * This method gets the package name the types are contained in. We do not talk about the diff here, since both the solution and the template type are supposed to be in
     * packages with the same names.
     *
     * @return The name of the package the solution and template types are contained in.
     */
    private String generatePackageName() {
        return solutionClass.getPackage().getName();
    }

    /**
     * This method generates the interface stereotype diff of the solution type and the template type, e.g. if the solution type is an interface and the template type is not.
     *
     * @return True, if the solution type is an interface and the template is not, false if they are both interfaces or not.
     */
    private boolean isInterfaceDifferent() {
        return isValueDifferent(JavaClass::isInterface);
    }

    /**
     * This method generates the enum stereotype diff of the solution type and the template type, e.g. if the solution type is an enum and the template type is not.
     *
     * @return True, if the solution type is an enum and the template is not, false if they are both enums or not.
     */
    private boolean isEnumDifferent() {
        return isValueDifferent(JavaClass::isEnum);
    }

    /**
     * This method generates the abstract modifier diff of the solution type and the template type, e.g. if the solution type is an abstract type and the template type is not.
     *
     * @return True, if the solution type is abstract and the template is not, false if they are both abstract or not abstract.
     */
    private boolean isAbstractDifferent() {
        return isValueDifferent(JavaClass::isAbstract);
    }

    private boolean isValueDifferent(Function<JavaClass, Boolean> valueGetter) {
        return templateClass == null ? valueGetter.apply(solutionClass) : (valueGetter.apply(solutionClass) && !valueGetter.apply(templateClass));
    }

    /**
     * This method generates the super class diff of the solution type and the template type, e.g. the name of the class the solution type extends and the template type does not.
     *
     * @return The simple name of the super class the solution type extends and the template type does not. If both types are subclasses of the same class, return an empty string.
     */
    private String generateSuperClassName() {
        JavaClass solutionSuperClass = solutionClass.getSuperJavaClass();
        if (solutionSuperClass != null && solutionSuperClass.getCanonicalName().equals("java.lang.Object")) {
            solutionSuperClass = null;
        }
        JavaClass templateSuperClass = templateClass == null ? null : templateClass.getSuperJavaClass();
        if (templateSuperClass != null && templateSuperClass.getCanonicalName().equals("java.lang.Object")) {
            templateSuperClass = null;
        }

        if (templateSuperClass == null) {
            // there was no superclass in the template
            if (solutionSuperClass == null) {
                return "";
            }
            else {
                return solutionSuperClass.getSimpleName();
            }
        }
        else {
            // there was a superclass and it might have changed
            if (solutionSuperClass == null) {
                return "Object";    // we explicitly need to test this case
            }
            else {
                return solutionSuperClass.getSimpleName();
            }
        }
    }

    /**
     * This method generates the super interfaces diff of the solution and template type, e.g. the interfaces the solution type implements, but the template type does not.
     *
     * @return A set of interfaces the solution type implements, but the template type does not.
     */
    private List<JavaClass> generateSuperInterfaces() {
        List<JavaClass> superInterfacesDiff = new ArrayList<>(solutionClass.getInterfaces());
        if (templateClass != null) {
            // Check all the super interfaces in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for (JavaClass templateTypeSuperInterface : templateClass.getInterfaces()) {
                // The interfaces are uniquely identified by their names.
                superInterfacesDiff.removeIf(solutionTypeSuperInterface -> solutionTypeSuperInterface.getSimpleName().equals(templateTypeSuperInterface.getSimpleName()));
            }
        }
        // If the template is non-existent, then the super interfaces diff consists of all the super interfaces of the solution type.
        return superInterfacesDiff;
    }

    /**
     * This method generates the annotations diff of the solution and template type, i.e. the annotations defined in the solution type but not in the template type.
     *
     * @return A set of annotations defined in the solution type but not in the template type.
     */
    private List<JavaAnnotation> generateAnnotationsDiff() {
        List<JavaAnnotation> annotationsDiff = new ArrayList<>(solutionClass.getAnnotations());
        if (templateClass != null) {
            for (JavaAnnotation templateAnnotation : templateClass.getAnnotations()) {
                // The annotations are uniquely identified by their names.
                annotationsDiff.removeIf(solutionAnnotation -> solutionAnnotation.getType().getSimpleName().equals(templateAnnotation.getType().getSimpleName()));
            }
        }
        return annotationsDiff;
    }

    /**
     * This method generates the attributes diff of the solution and template type, e.g. the attributes defined in the solution type but not in the template type.
     *
     * @return A set of attributes defined in the solution type but not in the template type.
     */
    private List<JavaField> generateAttributesDiff() {
        List<JavaField> attributesDiff = new ArrayList<>(solutionClass.getFields());
        // do not consider enum values as attributes
        attributesDiff.removeIf(JavaField::isEnumConstant);
        // If the template is non-existent, then the attributes diff consists of all the attributes of the solution type.
        removeTemplateElements(attributesDiff);
        return attributesDiff;
    }

    /**
     * This method generates the enums diff of the solution and template type, e.g. the enums defined in the solution type but not in the template type.
     *
     * @return A set of enums defined in the solution type but not in the template type.
     */
    private List<JavaField> generateEnumsDiff() {
        // Create an empty set of enums for the enums diff
        List<JavaField> enumsDiff = new ArrayList<>(solutionClass.getFields());
        // do not consider non enum fields
        enumsDiff.removeIf(field -> !field.isEnumConstant());
        removeTemplateElements(enumsDiff);
        return enumsDiff;
    }

    private void removeTemplateElements(List<JavaField> fieldDiff) {
        // If the template is non-existent, then the enum/attributes diff consists of all the attributes of the solution type.
        if (templateClass != null) {

            // Check all the enums/attributes in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for (JavaField templateField : templateClass.getFields()) {

                // Attributes and enums are uniquely identified by their name, type, modifiers and annotations.
                fieldDiff.removeIf(solutionField -> solutionField.getName().equals(templateField.getName()) && typesAreEqual(solutionField.getType(), templateField.getType())
                        && modifiersAreEqual(solutionField.getModifiers(), templateField.getModifiers())
                        && annotationsAreEqual(solutionField.getAnnotations(), templateField.getAnnotations()));
            }
        }
    }

    /**
     * This method generates the constructors diff of the solution and template type, e.g. the constructors defined in the solution type but not in the template type.
     *
     * @return A set of constructors defined in the solution type but not in the template type.
     */
    private List<JavaConstructor> generateConstructorsDiff() {

        List<JavaConstructor> constructorsDiff = new ArrayList<>(solutionClass.getConstructors());

        // If the template is non-existent, then the constructors diff consists of all the constructors of the solution type.
        if (templateClass != null) {

            // Check all the constructors in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for (JavaConstructor templateConstructor : templateClass.getConstructors()) {

                // The constructors are uniquely identified by their parameter types, modifiers and annotations
                constructorsDiff.removeIf(solutionConstructor -> parameterTypesAreEqual(solutionConstructor, templateConstructor)
                        && modifiersAreEqual(solutionConstructor.getModifiers(), templateConstructor.getModifiers())
                        && annotationsAreEqual(solutionConstructor.getAnnotations(), templateConstructor.getAnnotations()));
            }
        }
        return constructorsDiff;
    }

    /**
     * This method generates the methods diff of the solution and template type, e.g. the methods defined in the solution type but not in the template type.
     *
     * @return A set of methods defined in the solution type but not in the template type.
     */
    private List<JavaMethod> generateMethodsDiff() {
        List<JavaMethod> methodsDiff = new ArrayList<>(solutionClass.getMethods());
        methodsDiff.removeIf(method -> method.getName().equals("main"));

        if (templateClass != null) {

            // Check all the methods in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for (JavaMethod templateMethod : templateClass.getMethods()) {

                // The methods are uniquely identified by their names, parameter types, return type, modifiers and annotations.
                methodsDiff.removeIf(
                        solutionMethod -> signaturesAreEqual(solutionMethod, templateMethod) && modifiersAreEqual(solutionMethod.getModifiers(), templateMethod.getModifiers())
                                && typesAreEqual(solutionMethod.getReturns(), templateMethod.getReturns())
                                && annotationsAreEqual(solutionMethod.getAnnotations(), templateMethod.getAnnotations()));
            }
        }

        // If the template is non-existent, then the methods diff consists of all the methods of the solution type.
        return methodsDiff;
    }

    private boolean signaturesAreEqual(JavaMethod solutionMethod, JavaMethod templateMethod) {
        return methodNamesAreEqual(solutionMethod, templateMethod) && parameterTypesAreEqual(solutionMethod, templateMethod);
    }

    /**
     * This method checks if two given methods from the solution and template type respectively have the same name.
     *
     * @param solutionMethod The method defined in the solution type.
     * @param templateMethod The method defined in the template type.
     * @return True, if the name of the methods is the same, false otherwise.
     */
    private boolean methodNamesAreEqual(JavaMethod solutionMethod, JavaMethod templateMethod) {
        return solutionMethod.getName().equals(templateMethod.getName());
    }

    private boolean typesAreEqual(JavaClass solutionClass, JavaClass templateClass) {
        return solutionClass.getName().equals(templateClass.getName());
    }

    private boolean annotationsAreEqual(List<JavaAnnotation> solutionAnnotations, List<JavaAnnotation> templateAnnotations) {
        return checkListEquality(solutionAnnotations.stream().map(annotation -> annotation.getType().getSimpleName()).toList(),
                templateAnnotations.stream().map(annotation -> annotation.getType().getSimpleName()).toList());
    }

    private boolean modifiersAreEqual(List<String> solutionModifiers, List<String> templateModifiers) {
        return checkListEquality(solutionModifiers, templateModifiers);
    }

    private <T> boolean checkListEquality(List<T> solutionList, List<T> templateList) {
        if (solutionList == null && templateList == null) {
            return true;
        }
        else if (solutionList == null || templateList == null) {
            return false; // only one of them is null, the other is not
        }
        // If both have no content, then their content is the same.
        if (solutionList.isEmpty() && templateList.isEmpty()) {
            return true;
        }
        // If the size is not equal, then they are not the same.
        if (solutionList.size() != templateList.size()) {
            return false;
        }
        return new HashSet<>(solutionList).containsAll(templateList);
    }

    /**
     * This method checks if the parameter types of an executable in the solution type are the same to an executable in the template type. An executable can be a method or a
     * constructor.
     *
     * @param solutionExecutable: The executable present in the solution type.
     * @param templateExecutable: The executable present in the template type.
     * @return True, if the parameter types are the same, false otherwise.
     */
    private static boolean parameterTypesAreEqual(JavaExecutable solutionExecutable, JavaExecutable templateExecutable) {
        // Create lists containing only the parameter type names for both the executable.
        // This is done to work with them more easily, since types are uniquely identified only by their names.
        List<String> solutionParams = solutionExecutable.getParameters().stream().map(JavaParameter::getName).toList();
        List<String> templateParams = templateExecutable.getParameters().stream().map(JavaParameter::getName).toList();

        // If both executables have no parameters, then their parameters are the same.
        if (solutionParams.isEmpty() && templateParams.isEmpty()) {
            return true;
        }

        // If the number of the parameters is not equal, then their parameters are not the same.
        if (solutionParams.size() != templateParams.size()) {
            return false;
        }

        // Otherwise, check if the list of the parameters of the solution executable contains all the parameters in the template executable.
        return new HashSet<>(solutionParams).containsAll(templateParams);
    }

    /**
     * This method checks if the solution type is the same in structure as the template type.
     *
     * @return True, if the solution type is the same in structure as the template type, false otherwise.
     */
    boolean classesAreEqual() {
        return !this.isInterfaceDifferent && !this.isEnumDifferent && !this.isAbstractDifferent && this.superClassNameDiff.isEmpty() && this.superInterfacesDiff.isEmpty()
                && this.attributesDiff.isEmpty() && this.constructorsDiff.isEmpty() && this.methodsDiff.isEmpty() && this.annotationsDiff.isEmpty();
    }
}
