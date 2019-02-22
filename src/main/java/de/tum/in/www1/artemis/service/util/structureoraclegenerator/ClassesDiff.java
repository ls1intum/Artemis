package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import spoon.reflect.declaration.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static de.tum.in.www1.artemis.service.util.structureoraclegenerator.TypesDiff.parameterTypesAreEqual;

/**
 * This class extends the functionality of TypesDiff and handles structural elements that are exclusive to classes:
 * - Attributes,
 * - Constructors.
 */
public class ClassesDiff {

    private CtClass<?> solutionClass;
    private CtClass<?> templateClass;
    protected List<CtField<?>> attributes;
    protected Set<CtConstructor<?>> constructors;
    protected boolean classesEqual;
	
	public ClassesDiff(CtClass<?> solutionClass, CtClass<?> templateClass) {
	    this.solutionClass = solutionClass;
	    this.templateClass = templateClass;
		this.attributes = generateAttributesDiff();
		this.constructors = generateConstructorsDiff();
		this.classesEqual = areClassesEqual();
	}

    /**
     * This method generates the attributes diff of the solution and template type, e.g. the attributes defined in the
     * solution type but not in the template type.
     * @return A set of attributes defined in the solution type but not in the template type.
     */
    private List<CtField<?>> generateAttributesDiff() {
        // Use this predicate to filter out fields that are implicit, e.g. not explicitly defined in the code.
        Predicate<CtField<?>> fieldIsImplicit = field -> field.isImplicit() || field.getSimpleName().equals(solutionClass.getSimpleName());

        // Create an empty set of attribute for the attributes diff and deep-copy the methods of the solution type in it.
        List<CtField<?>> attributesDiff = new ArrayList<>(solutionClass.getFields());
        attributesDiff.removeIf(fieldIsImplicit);

        // If the template is non-existent, then the attributes diff consists of all the attributes of the solution type.
        if(templateClass != null) {

            // Check all the attributes in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for (CtField<?> templateAttribute : templateClass.getFields()) {

                // The fields are uniquely identified by their names.
                attributesDiff.removeIf(solutionAttribute -> solutionAttribute.getSimpleName().equals(templateAttribute.getSimpleName()));
            }
        }

        return attributesDiff;
    }

    /**
     * This method generates the constructors diff of the solution and template type, e.g. the constructors defined in the
     * solution type but not in the template type.
     * @return A set of constructors defined in the solution type but not in the template type.
     */
    private Set<CtConstructor<?>> generateConstructorsDiff() {
        // Use this predicate to filter out methods that are implicit, e.g. not explicitly defined in the code.
        Predicate<CtConstructor<?>> constructorIsImplicit = CtElement::isImplicit;

        // Create an empty set of constructors for the constructors diff and deep-copy the constructors of the solution type in it.
        Set<CtConstructor<?>> constructorsDiff = new HashSet<>(solutionClass.getConstructors());
        constructorsDiff.removeIf(constructorIsImplicit);

        // If the template is non-existent, then the constructors diff consists of all the constructors of the solution type.
        if(templateClass != null) {

            // Check all the constructors in the template type if they match to the ones in the solution type
            // and remove them from the diff, if that's the case.
            for(CtConstructor<?> templateConstructor : templateClass.getConstructors()) {

                // The constructors are uniquely identified by their parameter types.
                constructorsDiff.removeIf(solutionConstructor -> parameterTypesAreEqual(solutionConstructor, templateConstructor));
            }

        }

        return constructorsDiff;
    }

    /**
     * This method checks if the solution class is the same in structure as the template class.
     * @return True, if the solution type is the same in structure as the template type, false otherwise.
     */
	private boolean areClassesEqual() {
		return this.attributes.isEmpty() && this.constructors.isEmpty();
	}

}
