package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import spoon.reflect.declaration.CtInterface;

@JsonSerialize(using = InterfacesDiffSerializer.class)
public class InterfacesDiff extends TypesDiff {
	
	protected boolean interfacesEqual;
	
	public InterfacesDiff(CtInterface<?> solutionInterface, CtInterface<?> templateInterface) {
		super(solutionInterface, templateInterface);
		this.interfacesEqual = areInterfacesEqual();
	}

	private boolean areInterfacesEqual() {
		return areTypesEqual();
	}
	
}
