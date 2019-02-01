package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import spoon.reflect.declaration.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@JsonSerialize(using = ClassesDiffSerializer.class)
public class ClassesDiff extends TypesDiff {
	
    protected List<CtField<?>> attributes;
    protected Set<CtConstructor<?>> constructors;
    protected boolean classesEqual;
	
	public ClassesDiff(CtClass<?> solutionClass, CtClass<?> templateClass) {
		super(solutionClass, templateClass);
		this.attributes = generateAttributesDiff(solutionClass, templateClass);
		this.constructors = generateConstructorsDiff(solutionClass, templateClass);
		this.classesEqual = areClassesEqual();
	}

    private List<CtField<?>> generateAttributesDiff(CtClass<?> solutionClass, CtClass<?> templateClass) {
        Predicate<CtField<?>> fieldIsImplicit = f -> f.isImplicit() ||
            f.getSimpleName().equals(solutionClass.getSimpleName());

        List<CtField<?>> attributesDiff = new ArrayList<>();
        solutionClass.getFields().forEach(solutionField -> attributesDiff.add(solutionField));
        attributesDiff.removeIf(fieldIsImplicit);

        if (templateClass != null) {
            for (CtField<?> templateAttribute : templateClass.getFields()) {
                attributesDiff.removeIf(solutionAttribute ->
                    solutionAttribute.getSimpleName().equals(templateAttribute.getSimpleName()));
            }
        }

        return attributesDiff;
    }

    private Set<CtConstructor<?>> generateConstructorsDiff(CtClass<?> solutionClass, CtClass<?> templateClass) {
        Predicate<CtConstructor<?>> constructorIsImplicit = c -> c.isImplicit();

        Set<CtConstructor<?>> constructorsDiff = new HashSet<>();
        constructorsDiff.addAll(solutionClass.getConstructors());
        constructorsDiff.removeIf(constructorIsImplicit);

        if(templateClass != null) {
            for(CtConstructor<?> templateConstructor : templateClass.getConstructors()) {
                constructorsDiff.removeIf(solutionConstructor ->
                    parameterTypesAreEqual(solutionConstructor, templateConstructor));
            }

        }

        return constructorsDiff;
    }

	protected boolean areClassesEqual() {
		return super.areTypesEqual()
				&& this.attributes.isEmpty()
				&& this.constructors.isEmpty();
	}

}
