package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.function.Predicate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

/**
 * This class represents the so-called diff of the solution type and the template type. 
 * The solution type is a fully defined type found in the solution of a programming exercise.
 * The template type its is counterpart found in the template of the same exercise.
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
@JsonSerialize(using = TypesDiffSerializer.class)
public class TypesDiff {
    private CtType<?> solutionType;
    private CtType<?> templateType;
    protected boolean isTemplateNull;
    protected String name;
	protected String packageName;
	protected boolean isInterface;
	protected boolean isEnum;
	protected boolean isAbstract;
	protected String superClassName;
	protected Set<CtTypeReference<?>> superInterfacesNames;
	protected Set<CtMethod<?>> methods;
	protected boolean typesEqual;

	public TypesDiff(CtType<?> solutionType, CtType<?> templateType) {
	    this.solutionType = solutionType;
	    this.templateType = templateType;
        this.isTemplateNull = (templateType == null);
        this.name = generateName();
		this.packageName = generatePackageName();
		this.isInterface = generateInterfaceStereotype();
		this.isEnum = generateEnumStereotype();
		this.isAbstract = generateAbstractModifier();
		this.superClassName = generateSuperClassName();
		this.superInterfacesNames = generateSuperInterfaces();
		this.methods = generateMethodsDiff();
		this.typesEqual = areTypesEqual();
    }

    /**
     * This method gets the type name of both the solution and the template type. We do not talk about the diff here,
     * since both the solution and the template type are supposed to have the same name.
     * @return The name of the solution and template type.
     */
	private String generateName() {
	    return solutionType.getSimpleName();
	}

    /**
     * This method gets the package name the types are contained in. We do not talk about the diff here, since
     * both the solution and the template type are supposed to be in packages with the same names.
     * @return The name of the package the solution and template types are contained in.
     */
	private String generatePackageName() {
	    return solutionType.getPackage().getQualifiedName();
	}

    /**
     * This method generates the interface stereotype diff of the solution type and the template type, e.g. if the
     * solution type is an interface and the template type is not.
     * @return True, if the solution type is an interface and the template is not, false if they are both interfaces
     * or not.
     */
	private boolean generateInterfaceStereotype() {
	    return (isTemplateNull ? solutionType.isInterface() : (solutionType.isInterface() && !templateType.isInterface()));
	}

    /**
     * This method generates the enum stereotype diff of the solution type and the template type, e.g. if the
     * solution type is an enum and the template type is not.
     * @return True, if the solution type is an enum and the template is not, false if they are both enums
     * or not.
     */
	private boolean generateEnumStereotype() {
	    return (isTemplateNull ? solutionType.isEnum() : (solutionType.isEnum() && !templateType.isEnum()));
	}

    /**
     * This method generates the abstract modifier diff of the solution type and the template type, e.g. if the
     * solution type is an abstract type and the template type is not.
     * @return True, if the solution type is abstract and the template is not, false if they are both abstract
     * or not abstract.
     */
	private boolean generateAbstractModifier() {
	    return (isTemplateNull ? solutionType.isAbstract() : (solutionType.isAbstract() && !templateType.isAbstract()));
	}

    /**
     * This method generates the super class diff of the solution type and the template type, e.g. the name of the class
     * the solution type extends and the template type does not.
     * @return The simple name of the super class the solution type extends and the template type does not. If both
     * types are subclasses of the same class, return an empty string.
     */
	private String generateSuperClassName() {
		CtTypeReference<?> solutionSuperClass = solutionType.getSuperclass();
		CtTypeReference<?> templateSuperClass = (isTemplateNull || templateType.getSuperclass() == null) ? null : templateType.getSuperclass();
		
		return (solutionSuperClass != null && templateSuperClass == null) ? solutionSuperClass.getSimpleName() : "";
	}

    /**
     * This method generates the super interfaces diff of the solution and template type, e.g. the interfaces the
     * solution type implements, but the template type does not.
     * @return A set of interfaces the solution type implements, but the template type does not.
     */
	private Set<CtTypeReference<?>> generateSuperInterfaces() {
        // Use this predicate to filter out super interfaces that are implicit, e.g. not explicitly defined in the code.
        Predicate<CtTypeReference<?>> interfaceIsImplicit = CtElement::isImplicit;

        // Create an empty set of interfaces for the super interfaces diff and deep-copy the super interfaces of the solution type in it.
        Set<CtTypeReference<?>> superInterfacesDiff = new HashSet<>(solutionType.getSuperInterfaces());
		superInterfacesDiff.removeIf(interfaceIsImplicit);

		// If the template is non-existent, then the super interfaces diff consists of all the super interfaces of the solution type.
		if(!isTemplateNull) {

            // Check all the super interfaces in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for(CtTypeReference<?> templateTypeSuperInterface : templateType.getSuperInterfaces()) {

                // The interfaces are uniquely identified by their names.
		        superInterfacesDiff.removeIf(solutionTypeSuperInterface ->
                    solutionTypeSuperInterface.getSimpleName().equals(templateTypeSuperInterface.getSimpleName())
                );
            }
        }

		return superInterfacesDiff;
	}

    /**
     * This method generates the methods diff of the solution and template type, e.g. the methods defined in the
     * solution type but not in the template type.
     * @return A set of methods defined in the solution type but not in the template type.
     */
    private Set<CtMethod<?>> generateMethodsDiff() {
        // Use this predicate to filter out methods that are implicit, e.g. not explicitly defined in the code.
        Predicate<CtMethod<?>> methodIsImplicit = m -> m.isImplicit() || m.getSimpleName().equals("main") || m == null;

        // Create an empty set of methods for the methods diff and deep-copy the methods of the solution type in it.
        Set<CtMethod<?>> methodsDiff = new HashSet<>(solutionType.getMethods());
        methodsDiff.removeIf(methodIsImplicit);

        // If the template is non-existent, then the methods diff consists of all the methods of the solution type.
        if(!isTemplateNull) {

            // Check all the methods in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for(CtMethod<?> templateMethod : templateType.getMethods()) {

                // The methods are uniquely identified by their names and parameter types.
                methodsDiff.removeIf(solutionMethod ->
                    methodNamesAreEqual(solutionMethod, templateMethod) &&
                        parameterTypesAreEqual(solutionMethod, templateMethod));
            }
        }

        return methodsDiff;
    }

    /**
     * This method checks if two given methods from the solution and template type respectively have the same name.
     * @param solutionMethod The method defined in the solution type.
     * @param templateMethod The method defined in the template type.
     * @return True, if the name of the methods is the same, false otherwise.
     */
    private boolean methodNamesAreEqual(CtMethod<?> solutionMethod, CtMethod<?> templateMethod) {
        return solutionMethod.getSimpleName().equals(templateMethod.getSimpleName());
    }

    /**
     * This method checks if the parameter types of an executable in the solution type are the same to an executable
     * in the template type. An executable can be a method or a constructor.
     * @param solutionExecutable: The executable present in the solution type.
     * @param templateExecutable: The executable present in the template type.
     * @return True, if the parameter types are the same, false otherwise.
     */
    protected boolean parameterTypesAreEqual(CtExecutable<?> solutionExecutable, CtExecutable<?> templateExecutable) {
        // Create lists containing only the parameter type names for both the executable.
        // This is done to work with them more easily, since types are uniquely identified only by their names.
        List<String> solutionParams = new ArrayList<>();
        List<String> templateParams = new ArrayList<>();
        solutionExecutable.getParameters().forEach(parameter -> solutionParams.add(parameter.getSimpleName()));
        templateExecutable.getParameters().forEach(parameter -> templateParams.add(parameter.getSimpleName()));

        // If the number of the parameters is not equal, then the parameters are not the same.
        if(solutionParams.size() != templateParams.size()) {
            return false;
        }

        // If both executables have no empty, then they parameters are the same.
        if(solutionParams.isEmpty() && templateParams.isEmpty()) {
            return true;
        }

        // Otherwise, check if the list of the parameters of the solution executable contains all the parameters
        // in the template executable.
        return solutionParams.containsAll(templateParams);
    }

    /**
     * This method checks if the solution type is the same in structure as the template type.
     * @return True, if the solution type is the same in structure as the template type, false otherwise.
     */
	private boolean areTypesEqual() {
		return !this.isInterface
				&& !this.isEnum
				&& !this.isAbstract
				&& this.superClassName.isEmpty()
				&& this.superInterfacesNames.size() == 0
				&& this.methods.isEmpty();
	}

}
