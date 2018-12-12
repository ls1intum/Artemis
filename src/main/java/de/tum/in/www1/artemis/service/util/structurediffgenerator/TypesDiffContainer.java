package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.util.HashSet;
import java.util.Set;

import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class TypesDiffContainer {
	
	protected CtType<?> solutionType;
	protected CtType<?> templateType;
	
	protected String nameDiff;
	protected String packageNameDiff;
		
	protected boolean interfaceModifierDiff;
	protected boolean enumModifierDiff;
	protected boolean abstractModifierDiff;

	protected CtTypeReference<?> superClassDiff;
	protected Set<CtTypeReference<?>> superInterfacesDiff;
	
	MethodsDiffContainer methodsDiffContainer;
	
	protected boolean typesAreEqual;
	
	public TypesDiffContainer(CtType<?> solutionType, CtType<?> templateType) {
		this.solutionType = solutionType;
		this.templateType = templateType;
				
		this.nameDiff = generateNameDiff();
		this.packageNameDiff = generatePackageNameDiff();
				
		this.interfaceModifierDiff = generateInterfaceModifierDiff();
		this.enumModifierDiff = generateEnumModifierDiff();
		this.abstractModifierDiff = generateAbstractModifierDiff();

		this.superClassDiff = generateSuperClassDiff();
		this.superInterfacesDiff = generateSuperInterfacesDiff();	
		
		methodsDiffContainer = new MethodsDiffContainer(solutionType, templateType);
		
		this.typesAreEqual = generateTypesAreEqual();
	}
	
	public String getNameDiff() 								{ return this.nameDiff; }
	public String getPackageNameDiff()							{ return this.packageNameDiff; }
	
	public boolean getInterfaceModifierDiff()					{ return this.interfaceModifierDiff; }
	public boolean getEnumModifierDiff()						{ return this.enumModifierDiff; }
	public boolean getAbstractModifierDiff() 					{ return this.abstractModifierDiff; }

	public CtTypeReference<?> getSuperClassDiff()				{ return this.superClassDiff; }	
	public Set<CtTypeReference<?>> getSuperInterfacesDiff()		{ return this.superInterfacesDiff; }
	
	public MethodsDiffContainer getMethodsDiffContainer()		{ return this.methodsDiffContainer; }
	
	protected boolean getTypesAreEqual()						{ return this.typesAreEqual; }
		
	private String generateNameDiff() {
		return solutionType.getSimpleName();
	}
	
	private String generatePackageNameDiff() {
		return solutionType.getPackage().getQualifiedName();
	}
	
	private boolean generateInterfaceModifierDiff() {
		if(templateType == null) {
			return solutionType.isInterface();
		} else {
			return (solutionType.isInterface() && !templateType.isInterface());
		}		
	}
	
	private boolean generateEnumModifierDiff() {
		if(templateType == null) {
			return solutionType.isEnum();
		} else {
			return (solutionType.isEnum() && !templateType.isEnum());
		}
	}
	
	private boolean generateAbstractModifierDiff() {
		if(templateType == null) {
			return solutionType.isAbstract();
		} else {
			return (solutionType.isAbstract() && !templateType.isAbstract());
		}
	}
	
	private CtTypeReference<?> generateSuperClassDiff() {
		CtTypeReference<?> solutionSuperClass = solutionType.getSuperclass();
		CtTypeReference<?> templateSuperClass = (templateType == null) ? null : templateType.getSuperclass();
		
		return (solutionSuperClass != null && templateSuperClass == null) 
				? solutionSuperClass : null;
	}
	
	private Set<CtTypeReference<?>> generateSuperInterfacesDiff() {
		Set<CtTypeReference<?>> interfacesDiff = new HashSet<CtTypeReference<?>>();
		
		if(templateType == null) {
			interfacesDiff = solutionType.getSuperInterfaces();
		} else {
			for(CtTypeReference<?> currentSolutionInterface : solutionType.getSuperInterfaces()) {
				if(currentSolutionInterface.isImplicit()) {
					continue;
				}
				
				boolean interfaceImplementedByTemplate = false;
				
				for(CtTypeReference<?> currentTemplateInterface : templateType.getSuperInterfaces()) {
					
					// The interfaces are uniquely identified by their name
					if(currentSolutionInterface.getSimpleName().equals(currentTemplateInterface.getSimpleName())) {
						interfaceImplementedByTemplate = true;
						break;
					}
				}
				
				if(!interfaceImplementedByTemplate) {
					interfacesDiff.add(currentSolutionInterface);
				}
			}
		}
		
		return interfacesDiff;
	}
	
	protected boolean generateTypesAreEqual() {
		return this.interfaceModifierDiff
				&& !this.enumModifierDiff
				&& !this.abstractModifierDiff
				&& this.superClassDiff == null
				&& this.superInterfacesDiff.isEmpty()
				&& this.methodsDiffContainer.getMethodsDiff().isEmpty();
	}

}
