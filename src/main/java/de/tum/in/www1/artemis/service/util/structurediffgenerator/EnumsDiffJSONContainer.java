package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONArray;
import org.json.JSONObject;

public class EnumsDiffJSONContainer extends ClassesDiffJSONContainer {
	
	protected EnumsDiffContainer enumsDiffContainer;
		
	protected JSONObject enumsDiffJSON;
	
	public EnumsDiffJSONContainer(EnumsDiffContainer enumsDiffContainer) {
		super(enumsDiffContainer);
		this.enumsDiffContainer = enumsDiffContainer;
				
		this.enumsDiffJSON = generateEnumsDiffJSON();
	}
	
	public JSONObject getEnumsDiffJSON()			{ return this.enumsDiffJSON; }
	
	private JSONObject generateEnumsDiffJSON() {
		JSONObject enumsDiffJSON = getClassesDiffJSON();
		
		JSONArray enumValuesDiffJSON = generateEnumValuesDiffJSON();
		if(enumValuesDiffJSON.length() > 0) {
			enumsDiffJSON.put("enumValues", enumValuesDiffJSON);
		}
		
		return enumsDiffJSON;
	}
	
	private JSONArray generateEnumValuesDiffJSON() {
		JSONArray enumValuesDiffJSON = new JSONArray();
		
		for(String currentEnumValue : enumsDiffContainer.getEnumValuesDiff()) {
			enumValuesDiffJSON.put(currentEnumValue);
		}
		
		return enumValuesDiffJSON;
	}

}
