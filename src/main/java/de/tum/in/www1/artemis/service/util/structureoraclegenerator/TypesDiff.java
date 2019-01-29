package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.function.Predicate;

import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

public class TypesDiff {
    private CtType<?> solutionType;
    private CtType<?> templateType;
	protected String name;
	protected String packageName;
	protected boolean isInterface;
	protected boolean isEnum;
	protected boolean isAbstract;
	protected String superClassName;
	protected String[] superInterfacesNames;
	protected Set<CtMethod<?>> methods;
	protected boolean typesEqual;
	protected boolean isTemplateNull;
	
	public TypesDiff(CtType<?> solutionType, CtType<?> templateType) {
	    this.solutionType = solutionType;
	    this.templateType = templateType;
		this.name = generateName();
		this.packageName = generatePackageName();
		this.isInterface = generateInterfaceStereotype();
		this.isEnum = generateEnumStereotype();
		this.isAbstract = generateAbstractModifier();
		this.superClassName = generateSuperClassName();
		this.superInterfacesNames = generateSuperInterfacesNames();
		this.methods = generateMethodsDiff();
		this.typesEqual = areTypesEqual();
		this.isTemplateNull = templateType == null;
	}
		
	private String generateName() {
	    return solutionType.getSimpleName();
	}

	private String generatePackageName() {
		return solutionType.getPackage().getQualifiedName();
	}

	private boolean generateInterfaceStereotype() {
	    return (isTemplateNull ? solutionType.isInterface() : (solutionType.isInterface() && !templateType.isInterface()));
	}
	private boolean generateEnumStereotype() {
	    return (isTemplateNull ? solutionType.isEnum() : (solutionType.isEnum() && !templateType.isEnum()));
	}
	private boolean generateAbstractModifier() {
	    return (isTemplateNull ? solutionType.isAbstract() : (solutionType.isAbstract() && !templateType.isAbstract()));
	}
	private String generateSuperClassName() {
		CtTypeReference<?> solutionSuperClass = solutionType.getSuperclass();
		CtTypeReference<?> templateSuperClass = isTemplateNull ? null : templateType.getSuperclass();
		
		return (solutionSuperClass != null && templateSuperClass == null) ? solutionSuperClass.getSimpleName() : "";
	}
	
	private String[] generateSuperInterfacesNames() {
        Predicate<CtTypeReference<?>> interfaceIsImplicit = i -> i.isImplicit();

        Set<CtTypeReference<?>> superInterfacesDiff = solutionType.getSuperInterfaces();
		superInterfacesDiff.removeIf(interfaceIsImplicit);

		if(!isTemplateNull) {
            Set<CtTypeReference<?>> templateTypeSuperInterfaces = templateType.getSuperInterfaces();
            templateTypeSuperInterfaces.removeIf(interfaceIsImplicit);

            for(CtTypeReference<?> templateTypeSuperInterface : templateTypeSuperInterfaces) {
		        superInterfacesDiff.removeIf(solutionTypeSuperInterface ->
                    solutionTypeSuperInterface.getSimpleName().equals(templateTypeSuperInterface.getSimpleName())
                );
            }
        }

		Set<String> superInterfacesDiffNames = new HashSet<String>();
		superInterfacesDiff.forEach(superInterface -> superInterfacesDiffNames.add(superInterface.getSimpleName()));

		return (String[]) superInterfacesDiffNames.toArray();
	}

    private Set<CtMethod<?>> generateMethodsDiff() {
        Predicate<CtMethod<?>> methodIsImplicit = m -> m.isImplicit() || m.getSimpleName().equals("main");

        Set<CtMethod<?>> methodsDiff = solutionType.getMethods();
        methodsDiff.removeIf(methodIsImplicit);

        if(templateType != null) {
            Set<CtMethod<?>> templateMethods = templateType.getMethods();
            templateMethods.removeIf(methodIsImplicit);

            for(CtMethod<?> templateMethod : templateMethods) {
                methodsDiff.removeIf(solutionMethod ->
                    methodNamesAreEqual(solutionMethod, templateMethod) &&
                        parameterTypesAreEqual(solutionMethod, templateMethod));
            }
        }

        return methodsDiff;
    }

    private boolean methodNamesAreEqual(CtMethod<?> solutionMethod, CtMethod<?> templateMethod) {
        return solutionMethod.getSimpleName().equals(templateMethod.getSimpleName());
    }

    protected boolean parameterTypesAreEqual(CtExecutable<?> solutionExecutable, CtExecutable<?> templateExecutable) {
        List<CtParameter<?>> solutionParams = solutionExecutable.getParameters();
        List<CtParameter<?>> templateParams = templateExecutable.getParameters();

        if(solutionParams.size() != templateParams.size()) {
            return false;
        }

        if(solutionParams.isEmpty() && templateParams.isEmpty()) {
            return true;
        }

        boolean parametersAreRight = true;
        for(CtParameter<?> solutionParam : solutionParams) {
            for(CtParameter<?> templateParam : templateParams) {
                String solutionParameterType = solutionParam.getType().getSimpleName();
                String templateParameterType = templateParam.getType().getSimpleName();

                parametersAreRight &= solutionParameterType.equals(templateParameterType);
            }
        }

        return parametersAreRight;
    }
	
	protected boolean areTypesEqual() {
		return !this.isInterface
				&& !this.isEnum
				&& !this.isAbstract
				&& this.superClassName.isEmpty()
				&& this.superInterfacesNames.length == 0
				&& this.methods.isEmpty();
	}

}
