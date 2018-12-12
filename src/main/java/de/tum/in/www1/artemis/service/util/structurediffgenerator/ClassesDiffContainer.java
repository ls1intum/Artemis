package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import spoon.reflect.declaration.CtClass;

public class ClassesDiffContainer extends TypesDiffContainer {	
	
	protected AttributesDiffContainer attributesDiffContainer;
	protected ConstructorsDiffContainer constructorsDiffContainer;
	
	private boolean classesAreEqual;
	
	public ClassesDiffContainer(CtClass<?> solutionClass, CtClass<?> templateClass) {
		super(solutionClass, templateClass);
							
		this.attributesDiffContainer = new AttributesDiffContainer(solutionClass, templateClass);
		this.constructorsDiffContainer = new ConstructorsDiffContainer(solutionClass, templateClass);
		
		this.classesAreEqual = generateClassesAreEqual();
	}
	
	public AttributesDiffContainer getAttributesDiffContainer() 		{ return this.attributesDiffContainer; }	
	
	public ConstructorsDiffContainer getConstructorsDiffContainer()		{ return this.constructorsDiffContainer; }
	
	public boolean getClassesAreEqual()									{ return this.classesAreEqual; }
	
	protected boolean generateClassesAreEqual() {
		return super.getTypesAreEqual()
				&& this.attributesDiffContainer.getAttributesDiff().isEmpty()
				&& this.constructorsDiffContainer.getConstructorsDiff().isEmpty();
	}

}
