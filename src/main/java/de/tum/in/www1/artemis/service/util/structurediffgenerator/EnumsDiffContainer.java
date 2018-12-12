package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.util.ArrayList;
import java.util.List;

import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;

public class EnumsDiffContainer extends ClassesDiffContainer {
		
	private CtEnum<Enum<?>> solutionEnum;
	private CtEnum<Enum<?>> templateEnum;
	
	private List<String> enumValuesDiff;
	
	private boolean enumsAreEqual;
	
	public EnumsDiffContainer(CtEnum<Enum<?>> solutionEnum, CtEnum<Enum<?>> templateEnum) {
		super(solutionEnum, templateEnum);
		
		this.solutionEnum = solutionEnum;
		this.templateEnum = templateEnum;
		
		this.enumValuesDiff = generateEnumValuesDiff();
		
		this.enumsAreEqual = generateEnumsAreEqual();
	}
		
	public List<String> getEnumValuesDiff()		{ return this.enumValuesDiff; }
	
	public boolean getEnumsAreEqual() 			{ return this.enumsAreEqual; }

	private List<String> generateEnumValuesDiff() {
		if(templateEnum == null || templateEnum.getEnumValues().isEmpty()) {
			return filterEnumValues(solutionEnum.getEnumValues());		
		} else {
			List<String> enumValuesDiff = new ArrayList<String>();

			for(String currentSolutionEnumValue : filterEnumValues(solutionEnum.getEnumValues())) {
				boolean enumValuePresentInTemplate = false;
								
				for(String currentTemplateEnumValue : filterEnumValues(templateEnum.getEnumValues())) {
					if(currentSolutionEnumValue.equals(currentTemplateEnumValue)) {
						enumValuePresentInTemplate = true;
						break;
					}
				}
				
				if(!enumValuePresentInTemplate) {
					enumValuesDiff.add(currentSolutionEnumValue);
				}
			}
			
			return enumValuesDiff;
		}
	}
	
	private List<String> filterEnumValues(List<CtEnumValue<?>> enumValues) {
		List<String> filteredEnumValues = new ArrayList<String>();
		
		for(CtEnumValue<?> enumValue : enumValues) {
			if(!enumValue.isImplicit()) {
				filteredEnumValues.add(enumValue.getSimpleName());
			}
		}
				
		return filteredEnumValues;
	}
	
	private boolean generateEnumsAreEqual() {
		return super.generateClassesAreEqual()
				&& this.enumValuesDiff.isEmpty();
	}
	
}
