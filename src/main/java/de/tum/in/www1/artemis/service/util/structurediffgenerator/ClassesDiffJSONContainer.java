package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONObject;

public class ClassesDiffJSONContainer extends TypesDiffJSONContainer {
	
	protected ClassesDiffContainer classesDiffContainer;
	
	protected AttributesDiffJSONContainer attributesDiffJSONContainer;
	protected ConstructorsDiffJSONContainer constructorsDiffJSONContainer;
	
	protected JSONObject classesDiffJSON;
	
	public ClassesDiffJSONContainer(ClassesDiffContainer classesDiffContainer) {
		super(classesDiffContainer);
		this.classesDiffContainer = classesDiffContainer;
		
		this.attributesDiffJSONContainer = new AttributesDiffJSONContainer(classesDiffContainer.getAttributesDiffContainer());
		this.constructorsDiffJSONContainer = new ConstructorsDiffJSONContainer(classesDiffContainer.getConstructorsDiffContainer());
		
		this.classesDiffJSON = generateClassesDiffJSON();
	}
	
	public JSONObject getClassesDiffJSON() 		{ return this.classesDiffJSON; }
	
	private JSONObject generateClassesDiffJSON() {
		JSONObject classDiffJSON = getTypesDiffJSON();
		
		if(attributesDiffJSONContainer.getAttributesDiffJSON().length() > 0) {
			classDiffJSON.put("attributes", attributesDiffJSONContainer.getAttributesDiffJSON());
		}
		
		if(constructorsDiffJSONContainer.getConstructorsDiffJSON().length() > 0) {
			classDiffJSON.put("constructors", constructorsDiffJSONContainer.getConstructorsDiffJSON());
		}
				
		return classDiffJSON;
	}

}
