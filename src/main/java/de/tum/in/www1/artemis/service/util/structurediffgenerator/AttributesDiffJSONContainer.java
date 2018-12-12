package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import spoon.reflect.declaration.CtField;
import spoon.support.reflect.CtExtendedModifier;

public class AttributesDiffJSONContainer {
	
	private AttributesDiffContainer attributesDiffContainer;
	
	private JSONArray attributesDiffJSON;
	
	public AttributesDiffJSONContainer(AttributesDiffContainer attributesDiffContainer) {
		this.attributesDiffContainer = attributesDiffContainer;
		
		this.attributesDiffJSON = generateAttributesDiffJSON();
	}
	
	public JSONArray getAttributesDiffJSON()	{ return this.attributesDiffJSON; }
	
	private JSONArray generateAttributesDiffJSON() {
		JSONArray attributesDiffJSON = new JSONArray();
		
		for(CtField<?> currentAttribute : attributesDiffContainer.getAttributesDiff()) {
			JSONObject currentAttributeJSON = new JSONObject();
			
			currentAttributeJSON.put("name", currentAttribute.getSimpleName());
			
			JSONArray currentAttributeModifiers = generateAttributeModifiersJSONElement(currentAttribute);
			if(currentAttributeModifiers.length() > 0) {
				currentAttributeJSON.put("modifiers", currentAttributeModifiers);
			}
			
			currentAttributeJSON.put("type", currentAttribute.getType().getSimpleName());
			
			attributesDiffJSON.put(currentAttributeJSON);
		}
		
		return attributesDiffJSON;
	}
	
	private JSONArray generateAttributeModifiersJSONElement(CtField<?> attribute) {
		JSONArray attributeModifiersJSONElement = new JSONArray();
		
		for(CtExtendedModifier currentModifier : attribute.getExtendedModifiers()) {
			attributeModifiersJSONElement.put(currentModifier.getKind().toString());
		}
		
		return attributeModifiersJSONElement;
	}
	 
}
