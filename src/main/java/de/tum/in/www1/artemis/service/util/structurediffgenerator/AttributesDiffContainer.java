package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.util.ArrayList;
import java.util.List;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;

public class AttributesDiffContainer {
	
	private CtClass<?> solutionClass;
	private CtClass<?> templateClass;
	
	private List<CtField<?>> attributesDiff;
	
	public AttributesDiffContainer(CtClass<?> solutionClass, CtClass<?> templateClass) {
		this.solutionClass = solutionClass;
		this.templateClass = templateClass;
		
		this.attributesDiff = generateAttributesDiff();
	}
	
	public List<CtField<?>> getAttributesDiff()	{ return this.attributesDiff; }
	
	private List<CtField<?>> generateAttributesDiff() {
		if(templateClass == null || templateClass.getFields().isEmpty()) {
			return filterAttributes(solutionClass);
		} else {
			List<CtField<?>> attributesDiff = new ArrayList<CtField<?>>();

			for(CtField<?> currentSolutionAttribute : filterAttributes(solutionClass)) {
				boolean attributePresentInTemplate = false;
				
				for(CtField<?> currentTemplateAttribute : filterAttributes(templateClass)) {
					if(currentSolutionAttribute.getSimpleName().equals(currentTemplateAttribute.getSimpleName())) {
						attributePresentInTemplate = true;
						break;
					}
				}
				
				if(!attributePresentInTemplate ) {
					attributesDiff.add(currentSolutionAttribute);
				}
			}
			
			return attributesDiff;
		}
	}
	
	private List<CtField<?>> filterAttributes(CtClass<?> ctClass) {
		List<CtField<?>> filteredAttributes = new ArrayList<CtField<?>>();
		
		for(CtField<?> attribute : ctClass.getFields()) {
			if(!attribute.getType().getSimpleName().equals(ctClass.getSimpleName()) || !attribute.isImplicit()) {
				filteredAttributes.add(attribute);
			}
		}
		
		return filteredAttributes;
	}
	
}
