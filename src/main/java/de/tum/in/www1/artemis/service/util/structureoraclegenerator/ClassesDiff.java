package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import spoon.reflect.declaration.*;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class ClassesDiff extends TypesDiff {

    private CtClass<?> solutionClass;
    private CtClass<?> templateClass;
	
    protected List<CtField<?>> attributes;
    protected Set<CtConstructor<?>> constructors;

    protected boolean classesEqual;
	
	public ClassesDiff(CtClass<?> solutionClass, CtClass<?> templateClass) {
		super(solutionClass, templateClass);
		this.solutionClass = solutionClass;
		this.templateClass = templateClass;
		this.attributes = generateAttributesDiff();
		this.constructors = generateConstructorsDiff();
		this.classesEqual = areClassesEqual();
	}

    private List<CtField<?>> generateAttributesDiff() {
        Predicate<CtField<?>> fieldIsImplicit = f -> f.isImplicit() ||
            f.getSimpleName().equals(solutionClass.getSimpleName());

        List<CtField<?>> attributesDiff = solutionClass.getFields();
        attributesDiff.removeIf(fieldIsImplicit);

        if (templateClass != null) {
            List<CtField<?>> templateAttributes = templateClass.getFields();
            templateAttributes.removeIf(fieldIsImplicit);

            for (CtField<?> templateAttribute : templateAttributes) {
                attributesDiff.removeIf(solutionAttribute ->
                    namesAreEqual(solutionAttribute, templateAttribute));
            }
        }

        return attributesDiff;
    }

    protected boolean namesAreEqual(CtTypeMember solutionMember, CtTypeMember templateMember) {
        return solutionMember.getSimpleName().equals(templateMember.getSimpleName());
    }

    private Set<CtConstructor<?>> generateConstructorsDiff() {
        Predicate<CtConstructor<?>> constructorIsImplicit = c -> c.isImplicit();

        Set<CtConstructor<?>> constructorsDiff = solutionClass.getConstructors();
        constructorsDiff.removeIf(constructorIsImplicit);

        if(templateClass != null) {
            Set<CtConstructor<?>> templateConstructors = templateClass.getConstructors();
            templateConstructors.removeIf(constructorIsImplicit);

            for(CtConstructor<?> templateConstructor : templateConstructors) {
                constructorsDiff.removeIf(solutionConstructor ->
                    parameterTypesAreEqual(solutionConstructor, templateConstructor);
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
