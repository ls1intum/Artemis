package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

public class ConstructorsDiffJSONContainer {
	
	private ConstructorsDiffContainer constructorsDiffContainer;
	
	private JSONArray constructorsDiffJSON;
	
	public ConstructorsDiffJSONContainer(ConstructorsDiffContainer constructorsDiffContainer) {
		this.constructorsDiffContainer = constructorsDiffContainer;
		
		this.constructorsDiffJSON = generateConstructorsDiffJSON();
	}
	
	public JSONArray getConstructorsDiffJSON()		{ return this.constructorsDiffJSON; }
	
	private JSONArray generateConstructorsDiffJSON() {
		JSONArray constructorsDiffJSON = new JSONArray();
		
		for(CtConstructor<?> currentConstructor : constructorsDiffContainer.getConstructorsDiff()) {
			JSONObject currentConstructorJSON = new JSONObject();
			
			currentConstructorJSON.put("modifiers", generateConstructorModifiersJSONElement(currentConstructor));
			currentConstructorJSON.put("parameters", generateConstructorParametersJSONElement(currentConstructor));
			
			constructorsDiffJSON.put(currentConstructorJSON);
		}
		
		return constructorsDiffJSON;
	}
	
	private JSONArray generateConstructorModifiersJSONElement(CtConstructor<?> constructor) {
		JSONArray constructorModifiersJSONElement = new JSONArray();
		
		for(ModifierKind currentModifier : constructor.getModifiers()) {
			constructorModifiersJSONElement.put(currentModifier.toString());
		}
		
		return constructorModifiersJSONElement;
	}
	
	private JSONArray generateConstructorParametersJSONElement(CtConstructor<?> constructor) {
		JSONArray constructorParametersJSONElement = new JSONArray();
		
		for(CtParameter<?> currentParameter : constructor.getParameters()) {
			if(currentParameter.isImplicit()) {
				continue;
			}
			
			if(constructor.getDeclaringType().isEnum()) {
				constructorParametersJSONElement.put("String");
				constructorParametersJSONElement.put("int");
			}
			constructorParametersJSONElement.put(currentParameter.getType().getSimpleName());
		}
		
		return constructorParametersJSONElement;
	}

}
