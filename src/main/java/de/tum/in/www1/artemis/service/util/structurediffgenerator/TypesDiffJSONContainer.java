package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import spoon.reflect.reference.CtTypeReference;

public class TypesDiffJSONContainer {
	
	protected TypesDiffContainer typesDiffContainer;
	
	protected JSONObject typesDiffJSON;
	
	public TypesDiffJSONContainer(TypesDiffContainer typesDiffContainer) {
		this.typesDiffContainer = typesDiffContainer;
		
		this.typesDiffJSON = generateTypesDiffJSON();
	}
	
	public JSONObject getTypesDiffJSON() { return this.typesDiffJSON; }
	
	private JSONObject generateTypesDiffJSON() {
		JSONObject typesDiffPropertiesJSON = generateTypesDiffPropertiesJSONElement();
		JSONArray methodsDiffJSON = generateMethodsDiffJSONElement();
			
		JSONObject typesDiffJSON = new JSONObject();
		
		if(typesDiffPropertiesJSON.has("name") && typesDiffJSON.has("package")
			&& typesDiffPropertiesJSON.length() > 2) {
            typesDiffJSON.put("class", typesDiffPropertiesJSON);
        }

		if(methodsDiffJSON.length() > 0) {
			typesDiffJSON.put("methods", methodsDiffJSON);
		}
		
		return typesDiffJSON;
	}
	
	private JSONObject generateTypesDiffPropertiesJSONElement() {
		JSONObject typesDiffPropertiesJSONElement = new JSONObject();
		
		typesDiffPropertiesJSONElement.put("name", typesDiffContainer.getNameDiff());
		
		typesDiffPropertiesJSONElement.put("package", typesDiffContainer.getPackageNameDiff());
		
		String superClassDiffJSONElement = generateSuperClassDiffJSONElement();
		if(!superClassDiffJSONElement.isEmpty()) {
			typesDiffPropertiesJSONElement.put("superclass", superClassDiffJSONElement);
		}
		
		boolean interfaceModifierJSONElement = typesDiffContainer.getInterfaceModifierDiff();
		if(interfaceModifierJSONElement) {
			typesDiffPropertiesJSONElement.put("isInterface", interfaceModifierJSONElement);
		}
		
		boolean enumModifierJSONElement = typesDiffContainer.getEnumModifierDiff();
		if(enumModifierJSONElement) {
			typesDiffPropertiesJSONElement.put("isEnum", enumModifierJSONElement);
		}
		
		boolean abstractModifierJSONElement = typesDiffContainer.getAbstractModifierDiff();
		if(abstractModifierJSONElement) {
			typesDiffPropertiesJSONElement.put("isAbstract", abstractModifierJSONElement);
		}
		
		JSONArray superInterfacesDiffJSONElement = generateSuperInterfacesDiffJSONElement();
		if(superInterfacesDiffJSONElement.length() > 0) {
			typesDiffPropertiesJSONElement.put("interfaces", superInterfacesDiffJSONElement);
		}
		
		return typesDiffPropertiesJSONElement;
	}
	
	private String generateSuperClassDiffJSONElement() {
		CtTypeReference<?> superClassDiff = typesDiffContainer.getSuperClassDiff();
		return (superClassDiff == null) ? "" : superClassDiff.getSimpleName();
	}
	
	private JSONArray generateSuperInterfacesDiffJSONElement() {
		JSONArray interfacesDiffJSON = new JSONArray();
		
		for(CtTypeReference<?> currentInterface : typesDiffContainer.getSuperInterfacesDiff()) {
			interfacesDiffJSON.put(currentInterface.getSimpleName());
		}
		
		return interfacesDiffJSON;
	}
	
	private JSONArray generateMethodsDiffJSONElement() {
		MethodsDiffJSONContainer methodsDiffJSONContainer = new MethodsDiffJSONContainer(typesDiffContainer.getMethodsDiffContainer());
		return methodsDiffJSONContainer.getMethodsDiffJSON();
	}

}
