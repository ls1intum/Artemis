package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.thoughtworks.qdox.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents the so-called diff of the solution type and the template type. 
 * The solution type is a fully defined type found in the solution of a programming exercise.
 * The template type is its counterpart found in the template of the same exercise.
 * It is clear that the template type misses some of the structural elements of the solution type.
 * This diff contains information on these structural elements.
 * The ones the diff currently handles is:
 * - Class name,
 * - Package name,
 * - Interface stereotype,
 * - Enum stereotype,
 * - Abstract modifier,
 * - Superclass name,
 * - Superinterfaces names,
 * - Methods.
 */
public class TypesDiff {

    private JavaClass solutionClass;
    private JavaClass templateClass;
    protected String name;
	protected String packageName;
	protected boolean isInterface;
	protected boolean isEnumDifferent;
	protected boolean isAbstractDifferent;
	protected String superClassName;

	protected List<JavaClass> superInterfaces;
	protected List<JavaMethod> methodsDiff;
    protected List<JavaField> attributesDiff;
    protected List<JavaConstructor> constructorsDiff;

	protected boolean typesEqual;

	public TypesDiff(JavaClass solutionClass, JavaClass templateClass) {
	    this.solutionClass = solutionClass;
	    this.templateClass = templateClass;
        this.name = generateName();
		this.packageName = generatePackageName();
		this.isInterface = generateInterfaceStereotype();
		this.isEnumDifferent = generateEnumStereotype();
		this.isAbstractDifferent = generateAbstractModifier();
		this.superClassName = generateSuperClassName();
		this.superInterfaces = generateSuperInterfaces();
		this.attributesDiff = generateAttributesDiff();
		this.constructorsDiff = generateConstructorsDiff();
		this.methodsDiff = generateMethodsDiff();
		this.typesEqual = areTypesEqual();
    }

    /**
     * This method gets the type name of both the solution and the template type. We do not talk about the diff here,
     * since both the solution and the template type are supposed to have the same name.
     * @return The name of the solution and template type.
     */
	private String generateName() {
	    return solutionClass.getSimpleName();
	}

    /**
     * This method gets the package name the types are contained in. We do not talk about the diff here, since
     * both the solution and the template type are supposed to be in packages with the same names.
     * @return The name of the package the solution and template types are contained in.
     */
	private String generatePackageName() {
	    return solutionClass.getPackage().getName();
	}

    /**
     * This method generates the interface stereotype diff of the solution type and the template type, e.g. if the
     * solution type is an interface and the template type is not.
     * @return True, if the solution type is an interface and the template is not, false if they are both interfaces
     * or not.
     */
	private boolean generateInterfaceStereotype() {
	    return (templateClass == null ? solutionClass.isInterface() : (solutionClass.isInterface() && !templateClass.isInterface()));
	}

    /**
     * This method generates the enum stereotype diff of the solution type and the template type, e.g. if the
     * solution type is an enum and the template type is not.
     * @return True, if the solution type is an enum and the template is not, false if they are both enums
     * or not.
     */
	private boolean generateEnumStereotype() {
	    return (templateClass == null ? solutionClass.isEnum() : (solutionClass.isEnum() && !templateClass.isEnum()));
	}

    /**
     * This method generates the abstract modifier diff of the solution type and the template type, e.g. if the
     * solution type is an abstract type and the template type is not.
     * @return True, if the solution type is abstract and the template is not, false if they are both abstract
     * or not abstract.
     */
	private boolean generateAbstractModifier() {
	    return (templateClass == null ? solutionClass.isAbstract() : (solutionClass.isAbstract() && !templateClass.isAbstract()));
	}

    /**
     * This method generates the super class diff of the solution type and the template type, e.g. the name of the class
     * the solution type extends and the template type does not.
     * @return The simple name of the super class the solution type extends and the template type does not. If both
     * types are subclasses of the same class, return an empty string.
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
		    // there was not super class in the template
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
                return "Object";    // we explicitely need to test this case
            }
            else {
                return solutionSuperClass.getSimpleName();
            }
        }
	}

    /**
     * This method generates the super interfaces diff of the solution and template type, e.g. the interfaces the
     * solution type implements, but the template type does not.
     * @return A set of interfaces the solution type implements, but the template type does not.
     */
	private List<JavaClass> generateSuperInterfaces() {

        // Create an empty list of interfaces for the super interfaces diff and deep-copy the super interfaces of the solution type in it.
        List<JavaClass> superInterfacesDiff = solutionClass.getInterfaces();

		if(templateClass != null) {

            // Check all the super interfaces in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for(JavaClass templateTypeSuperInterface : templateClass.getInterfaces()) {

                // The interfaces are uniquely identified by their names.
		        superInterfacesDiff.removeIf(solutionTypeSuperInterface ->
                    solutionTypeSuperInterface.getSimpleName().equals(templateTypeSuperInterface.getSimpleName())
                );
            }
        }

        // If the template is non-existent, then the super interfaces diff consists of all the super interfaces of the solution type.
		return superInterfacesDiff;
	}

    /**
     * This method generates the attributes diff of the solution and template type, e.g. the attributes defined in the
     * solution type but not in the template type.
     * @return A set of attributes defined in the solution type but not in the template type.
     */
    private List<JavaField> generateAttributesDiff() {
        // Create an empty set of attribute for the attributes diff and deep-copy the methods of the solution type in it.
        List<JavaField> attributesDiff = solutionClass.getFields();
        attributesDiff.removeIf(field -> field.getName().equals(solutionClass.getName()));

        // If the template is non-existent, then the attributes diff consists of all the attributes of the solution type.
        if(templateClass != null) {

            // Check all the attributes in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for (JavaField templateAttribute : templateClass.getFields()) {

                // The fields are uniquely identified by their names.
                attributesDiff.removeIf(solutionAttribute -> solutionAttribute.getName().equals(templateAttribute.getName()));
            }
        }

        return attributesDiff;
    }

    /**
     * This method generates the constructors diff of the solution and template type, e.g. the constructors defined in the
     * solution type but not in the template type.
     * @return A set of constructors defined in the solution type but not in the template type.
     */
    private List<JavaConstructor> generateConstructorsDiff() {

        List<JavaConstructor> constructorsDiff = solutionClass.getConstructors();

        // If the template is non-existent, then the constructors diff consists of all the constructors of the solution type.
        if(templateClass != null) {

            // Check all the constructors in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for(JavaConstructor templateConstructor : templateClass.getConstructors()) {

                // The constructors are uniquely identified by their parameter types.
                constructorsDiff.removeIf(solutionConstructor -> parameterTypesAreEqual(solutionConstructor, templateConstructor));
            }
        }
        return constructorsDiff;
    }


    /**
     * This method generates the methods diff of the solution and template type, e.g. the methods defined in the
     * solution type but not in the template type.
     * @return A set of methods defined in the solution type but not in the template type.
     */
    private List<JavaMethod> generateMethodsDiff() {
        List<JavaMethod> methodsDiff = solutionClass.getMethods();
        methodsDiff.removeIf(method -> method.getName().equals("main"));

        if(templateClass != null) {

            // Check all the methods in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for(JavaMethod templateMethod : templateClass.getMethods()) {

                // The methods are uniquely identified by their names and parameter types.
                methodsDiff.removeIf(solutionMethod -> signaturesAreEqual(solutionMethod, templateMethod));
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
     * @param solutionMethod The method defined in the solution type.
     * @param templateMethod The method defined in the template type.
     * @return True, if the name of the methods is the same, false otherwise.
     */
    private boolean methodNamesAreEqual(JavaMethod solutionMethod, JavaMethod templateMethod) {
        return solutionMethod.getName().equals(templateMethod.getName());
    }

    /**
     * This method checks if the parameter types of an executable in the solution type are the same to an executable
     * in the template type. An executable can be a method or a constructor.
     * @param solutionExecutable: The executable present in the solution type.
     * @param templateExecutable: The executable present in the template type.
     * @return True, if the parameter types are the same, false otherwise.
     */
    public static boolean parameterTypesAreEqual(JavaExecutable solutionExecutable, JavaExecutable templateExecutable) {
        // Create lists containing only the parameter type names for both the executable.
        // This is done to work with them more easily, since types are uniquely identified only by their names.
        List<String> solutionParams = solutionExecutable.getParameters().stream().map(JavaParameter::getName).collect(Collectors.toList());
        List<String> templateParams = templateExecutable.getParameters().stream().map(JavaParameter::getName).collect(Collectors.toList());;

        // If both executables have no parameters, then their parameters are the same.
        if(solutionParams.isEmpty() && templateParams.isEmpty()) return true;

        // If the number of the parameters is not equal, then their parameters are not the same.
        if(solutionParams.size() != templateParams.size()) return false;

        // Otherwise, check if the list of the parameters of the solution executable contains all the parameters
        // in the template executable.
        return solutionParams.containsAll(templateParams);
    }

    /**
     * This method checks if the solution type is the same in structure as the template type.
     * @return True, if the solution type is the same in structure as the template type, false otherwise.
     */
	private boolean areTypesEqual() {
		return this.isInterface
				&& !this.isEnumDifferent
				&& !this.isAbstractDifferent
				&& this.superClassName.isEmpty()
				&& this.superInterfaces.isEmpty()
                && this.attributesDiff.isEmpty()
                && this.constructorsDiff.isEmpty()
                && this.methodsDiff.isEmpty();
	}

}
