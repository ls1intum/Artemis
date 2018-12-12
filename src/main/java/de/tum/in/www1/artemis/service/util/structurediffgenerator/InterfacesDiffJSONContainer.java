package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import org.json.JSONObject;

public class InterfacesDiffJSONContainer extends TypesDiffJSONContainer {
	
	protected InterfacesDiffContainer interfacesDiffContainer;
	
	protected JSONObject interfacesDiffJSON;
	
	public InterfacesDiffJSONContainer(InterfacesDiffContainer interfacesDiffContainer) {
		super(interfacesDiffContainer);
		this.interfacesDiffContainer = interfacesDiffContainer;
		
		this.interfacesDiffJSON = generateInterfacesDiffJSON();
	}
	
	public JSONObject getInterfacesDiffJSON() 	{ return this.interfacesDiffJSON; }
	
	private JSONObject generateInterfacesDiffJSON() {
		JSONObject interfacesDiffJSON = getTypesDiffJSON();
				
		return interfacesDiffJSON;
	}

}
