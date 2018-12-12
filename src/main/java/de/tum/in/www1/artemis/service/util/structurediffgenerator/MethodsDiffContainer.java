package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;

public class MethodsDiffContainer {
	
	private CtType<?> solutionType;
	private CtType<?> templateType;
	
	private Set<CtMethod<?>> methodsDiff;
	
	public MethodsDiffContainer(CtType<?> solutionType, CtType<?> templateType) {
		this.solutionType = solutionType;
		this.templateType = templateType;
		
		this.methodsDiff = generateMethodsDiff();
	}
	
	public Set<CtMethod<?>> getMethodsDiff() 		{ return this.methodsDiff;}
	
	private Set<CtMethod<?>> generateMethodsDiff() {
		if(templateType == null || templateType.getMethods().isEmpty()) {
			return filterMethods(solutionType);
		} else {
			Set<CtMethod<?>> methodsDiff = new HashSet<CtMethod<?>>();

			for(CtMethod<?> currentSolutionMethod : filterMethods(solutionType)) {
				boolean methodPresentInTemplate = false;
												
				for(CtMethod<?> currentTemplateMethod : filterMethods(templateType)) {
					if(compareMethodNames(currentSolutionMethod, currentTemplateMethod) &&
							compareMethodParameterTypes(currentSolutionMethod, currentTemplateMethod)) {
						methodPresentInTemplate = true;
						break;
					}
				}
				
				if(!methodPresentInTemplate) {
					methodsDiff.add(currentSolutionMethod);
				}
			}
			
			return methodsDiff;
		}
		
	}
	
	private boolean compareMethodNames(CtMethod<?> solutionMethod, CtMethod<?> templateMethod) {
		return solutionMethod.getSimpleName().equals(templateMethod.getSimpleName());
	}
	
	private boolean compareMethodParameterTypes(CtMethod<?> solutionMethod, CtMethod<?> templateMethod) {
		List<CtParameter<?>> solutionMethodParams = solutionMethod.getParameters();
		List<CtParameter<?>> templateMethodParams = templateMethod.getParameters();
		
		if(solutionMethodParams.size() != templateMethodParams.size()) {
			return false;
		}
		
		if(solutionMethodParams.isEmpty() && templateMethodParams.isEmpty()) {
			return true;
		}
				
		boolean allParametersAreInTemplate = true;
		for(CtParameter<?> currentSolutionMethodParam : solutionMethodParams) {	
			for(CtParameter<?> currentTemplateMethodParam : templateMethodParams) {
				String solutionParameterType = currentSolutionMethodParam.getType().getSimpleName();
				String templateParameterType = currentTemplateMethodParam.getType().getSimpleName();
				
				allParametersAreInTemplate &= solutionParameterType.equals(templateParameterType);
			}
			
		}
		
		return allParametersAreInTemplate;
	}
	
	private Set<CtMethod<?>> filterMethods(CtType<?> ctType) {
		Set<CtMethod<?>> filteredMethods = new HashSet<CtMethod<?>>();
		
		for(CtMethod<?> method : ctType.getMethods()) {
			if(!method.isImplicit() || !method.getSimpleName().equals("main")) {
				filteredMethods.add(method);
			}
		}	
		
		return filteredMethods;
	}

}
