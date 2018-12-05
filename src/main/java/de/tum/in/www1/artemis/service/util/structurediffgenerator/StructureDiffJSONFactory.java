package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;

public class StructureDiffJSONFactory {

	public static JSONArray generateStructureDiffJSON(String solutionProjectPath, String templateProjectPath) {
		HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = generateSolutionAndTemplateTypePairs(solutionProjectPath, templateProjectPath);
		
		JSONArray structureDiffJSON = new JSONArray();
		
		for(Map.Entry<CtType<?>, CtType<?>> currentEntry : solutionAndTemplateTypes.entrySet()) {
			CtType<?> currentSolutionType = currentEntry.getKey();
			CtType<?> currentTemplateType = currentEntry.getValue();
			
			String currentTypeDiffName = "";
			JSONObject currentTypeDiffJSON = new JSONObject();
			
			// Get the enums and add them to the hash map
			if(currentSolutionType.isEnum()) {
				CtEnum<Enum<?>> currentSolutionEnum = (CtEnum<Enum<?>>) currentSolutionType;
				CtEnum<Enum<?>> currentTemplateEnum;
				
				if(currentTemplateType == null || !currentTemplateType.isEnum()) {
					currentTemplateEnum = null;
				} else {
					currentTemplateEnum = (CtEnum<Enum<?>>) currentTemplateType;
				}
									
				// The key of currentEntry is the solution class and the value is the template class
				EnumsDiffContainer currentEnumsDiffContainer = new EnumsDiffContainer(currentSolutionEnum, currentTemplateEnum);
				if(currentEnumsDiffContainer.getEnumsAreEqual()) {
					continue;
				}
				
				currentTypeDiffName = currentEnumsDiffContainer.getNameDiff();
				
				EnumsDiffJSONContainer currentEnumsDiffJSONContainer = new EnumsDiffJSONContainer(currentEnumsDiffContainer);
				currentTypeDiffJSON = currentEnumsDiffJSONContainer.getEnumsDiffJSON();
			}
			
			// Get the interfaces and add them to the hash map
			if(currentSolutionType.isInterface()) {
				CtInterface<?> currentSolutionInterface = (CtInterface<?>) currentSolutionType;
				CtInterface<?> currentTemplateInterface = null;
				
				if(currentTemplateType == null || !currentTemplateType.isInterface()) {
					currentTemplateInterface = null;
				} else {
					currentTemplateInterface = (CtInterface<?>) currentTemplateType;
				}
				
				currentTypeDiffName = currentSolutionInterface.getSimpleName();
					
				InterfacesDiffContainer currentInterfacesDiffContainer = new InterfacesDiffContainer(currentSolutionInterface, currentTemplateInterface);
				
				if(currentInterfacesDiffContainer.getInterfacesEquality()) {
					continue;
				}
				
				currentTypeDiffName = currentInterfacesDiffContainer.getNameDiff();
				
				InterfacesDiffJSONContainer currentInterfacesDiffJSONContainer = new InterfacesDiffJSONContainer(currentInterfacesDiffContainer);
				currentTypeDiffJSON = currentInterfacesDiffJSONContainer.getInterfacesDiffJSON();
			}
			
			// Get the classes and add them to the hash map
			if(currentSolutionType.isClass()) {
				CtClass<?> currentSolutionClass = (CtClass<?>) currentSolutionType;
				CtClass<?> currentTemplateClass = null;
				
				if(currentTemplateType == null || !currentTemplateType.isClass()) {
					currentTemplateClass = null;
				} else {
					currentTemplateClass = (CtClass<?>) currentTemplateType;
				}
				
				currentTypeDiffName = currentSolutionClass.getSimpleName();
					
				// The key of currentEntry is the solution class and the value is the template class
				ClassesDiffContainer currentClassesDiffContainer = new ClassesDiffContainer(currentSolutionClass, currentTemplateClass);
				
				if(currentClassesDiffContainer.getClassesAreEqual()) {
					continue;
				}
		
				currentTypeDiffName = currentClassesDiffContainer.getNameDiff();
				
				ClassesDiffJSONContainer currentClassesDiffJSONContainer = new ClassesDiffJSONContainer(currentClassesDiffContainer);		
				currentTypeDiffJSON = currentClassesDiffJSONContainer.getClassesDiffJSON();
			}
			
			System.out.println("Creating JSON for: " + currentTypeDiffName);
			structureDiffJSON.put(currentTypeDiffJSON);
		}
		
		return structureDiffJSON;
	}
		
	private static HashMap<CtType<?>, CtType<?>> generateSolutionAndTemplateTypePairs(String solutionProjectPath, String templateProjectPath) {
		CtModel solutionModel = generateModel(solutionProjectPath);
		CtModel templateModel = generateModel(templateProjectPath);
		
		HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = new HashMap<CtType<?>, CtType<?>>();
		
		// Types are uniquely identified by their names
		for(CtType<?> currentSolutionType : solutionModel.getAllTypes()) {
			boolean typePresentInTemplate = false;
			
			for(CtType<?> currentTemplateType : templateModel.getAllTypes()) {
				if(currentSolutionType.getSimpleName().equals(currentTemplateType.getSimpleName())) {
					typePresentInTemplate = true;
					
					// Ignore if the types are the same
					if(!currentSolutionType.equals(currentTemplateType)) {
						solutionAndTemplateTypes.put(currentSolutionType, currentTemplateType);
						break;
					}
				}
			}
			
			// Add a null template type, if it is not present
			if(!typePresentInTemplate) {
				solutionAndTemplateTypes.put(currentSolutionType, null);
			}
		}
		
		return solutionAndTemplateTypes;
	}
	
	private static CtModel generateModel(String projectPath) {
		Launcher launcher = new Launcher();
		launcher.addInputResource(projectPath); 
		launcher.buildModel();
		return launcher.getModel();
	}
	
}
