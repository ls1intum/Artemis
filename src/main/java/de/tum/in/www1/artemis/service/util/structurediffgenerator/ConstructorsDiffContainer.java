package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtParameter;

public class ConstructorsDiffContainer {
	
	private CtClass<?> solutionClass;
	private CtClass<?> templateClass;
	
	private Set<CtConstructor<?>> constructorsDiff;

	public ConstructorsDiffContainer(CtClass<?> solutionClass, CtClass<?> templateClass) {
		this.solutionClass = solutionClass;
		this.templateClass = templateClass;
		
		this.constructorsDiff = generateConstructorsDiff();
	}
	
	public Set<CtConstructor<?>> getConstructorsDiff()		{ return this.constructorsDiff; }
	
	private Set<CtConstructor<?>> generateConstructorsDiff() {
		if(templateClass == null || templateClass.getConstructors().isEmpty()) {
			return filterConstructors(solutionClass);
		} else {
			Set<CtConstructor<?>> constructorsDiff = new HashSet<CtConstructor<?>>();
			
			for(CtConstructor<?> currentSolutionConstructor : filterConstructors(solutionClass)) {
				boolean constructorPresentInTemplate = false;
				
				for(CtConstructor<?> currentTemplateConstructor : filterConstructors(templateClass)) {
					if(compareConstructorParameters(currentSolutionConstructor, currentTemplateConstructor)) {
						constructorPresentInTemplate = true;
						break;
					}
				}
				
				if(!constructorPresentInTemplate) {
					constructorsDiff.add(currentSolutionConstructor);
				}
			}
			
			return constructorsDiff;
		}
	}

	private boolean compareConstructorParameters(CtConstructor<?> solutionConstructor, CtConstructor<?> templateConstructor) {
		List<CtParameter<?>> solutionConstructorParams = solutionConstructor.getParameters();
		List<CtParameter<?>> templateConstructorParams = templateConstructor.getParameters();
		
		if(solutionConstructorParams.size() != templateConstructorParams.size()) {
			return false;
		}
		
		if(solutionConstructorParams.isEmpty() && templateConstructorParams.isEmpty()) {
			return true;
		}
		
		boolean allParametersAreInTemplate = true;
		for(CtParameter<?> currentSolutionConstructorParam : solutionConstructorParams) {
			for(CtParameter<?> currentTemplateConstructorParam : templateConstructorParams) {
				String solutionParameterType = currentSolutionConstructorParam.getType().getSimpleName();
				String templateParameterType = currentTemplateConstructorParam.getType().getSimpleName();
				
				allParametersAreInTemplate &= solutionParameterType.equals(templateParameterType);
			}
		}
		
		return allParametersAreInTemplate;
	}

	private Set<CtConstructor<?>> filterConstructors(CtClass<?> ctClass) {
		Set<CtConstructor<?>> filteredConstructors = new HashSet<CtConstructor<?>>();

		for(CtConstructor<?> constructor : ctClass.getConstructors()) {
			if(!constructor.isImplicit()) {
				filteredConstructors.add(constructor);
			}
		}
		
		return filteredConstructors;
	}
	
}
