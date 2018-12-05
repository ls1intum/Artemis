package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import spoon.reflect.declaration.CtInterface;

public class InterfacesDiffContainer extends TypesDiffContainer {
	
	private boolean interfacesEquality;
	
	public InterfacesDiffContainer(CtInterface<?> solutionInterface, CtInterface<?> templateInterface) {
		super(solutionInterface, templateInterface);
		
		this.interfacesEquality = generateInterfacesEquality();
	}
	
	public boolean getInterfacesEquality() { return this.interfacesEquality; }
	
	private boolean generateInterfacesEquality() {
		return getTypesAreEqual();
	}
	
}
