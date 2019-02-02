package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import spoon.reflect.declaration.CtInterface;

/**
 * This diff extends the functionality of TypesDiff and is supposed to handle the structural elements that are
 * exclusive to interfaces. We currently do not test any other interface elements that are not already handled
 * in TypesDiff, therefore the functionality is the same. The InterfacesDiff is currently used to allow easier extension
 * and differentiation from other subtypes while generating the structure oracle.
 */
@JsonSerialize(using = InterfacesDiffSerializer.class)
public class InterfacesDiff extends TypesDiff {
	
	protected boolean interfacesEqual;
	
	public InterfacesDiff(CtInterface<?> solutionInterface, CtInterface<?> templateInterface) {
		super(solutionInterface, templateInterface);
		this.interfacesEqual = areInterfacesEqual();
	}

    /**
     * This method checks if the solution interface is the same in structure as the template interface.
     * @return True, if the solution interface is the same in structure as the template interface, false otherwise.
     */
	private boolean areInterfacesEqual() {
		return typesEqual;
	}
	
}
