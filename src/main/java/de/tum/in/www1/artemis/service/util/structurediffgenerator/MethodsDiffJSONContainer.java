package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

public class MethodsDiffJSONContainer {
	
	private MethodsDiffContainer methodsDiffContainer;
	
	private JSONArray methodsDiffJSON;
	
	public MethodsDiffJSONContainer(MethodsDiffContainer methodsDiffContainer) {
		this.methodsDiffContainer = methodsDiffContainer;
		
		this.methodsDiffJSON = generateMethodsDiffJSON();
	}
	
	public JSONArray getMethodsDiffJSON() { return this.methodsDiffJSON; }
	
	private JSONArray generateMethodsDiffJSON() {
		JSONArray methodsDiffJSON = new JSONArray();
		
		for(CtMethod<?> currentMethod : methodsDiffContainer.getMethodsDiff()) {
			JSONObject currentMethodJSON = new JSONObject();
			
			currentMethodJSON.put("name", currentMethod.getSimpleName());	
			currentMethodJSON.put("modifiers", generateMethodModifiersJSONElement(currentMethod));
			currentMethodJSON.put("parameters", generateMethodParametersJSONElement(currentMethod));	
			currentMethodJSON.put("returnType", currentMethod.getType().getSimpleName());
			
			methodsDiffJSON.put(currentMethodJSON);
		}
		
		return methodsDiffJSON;
	}
	
	private JSONArray generateMethodModifiersJSONElement(CtMethod<?> method) {
		JSONArray methodModifiersJSONElement = new JSONArray();
		
		for(ModifierKind currentModifier : method.getModifiers()) {	
			methodModifiersJSONElement.put(currentModifier.toString());
		}

		return methodModifiersJSONElement;
	}
	
	private JSONArray generateMethodParametersJSONElement(CtMethod<?> method) {
		JSONArray methodParametersJSONElement = new JSONArray();
		
		for(CtParameter<?> currentParameter : method.getParameters()) {
			if(currentParameter.isImplicit()) {
				continue;
			}
			
			methodParametersJSONElement.put(currentParameter.getType().getSimpleName());
		}
		
		return methodParametersJSONElement;
	}

}
