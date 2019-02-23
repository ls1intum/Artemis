package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * This diff extends the functionality of ClassesDiff and handles structural elements that are exclusive to enums:
 *  - Enum values.
 */
public class EnumsDiff {

    private CtEnum<Enum<?>> solutionEnum;
    private CtEnum<Enum<?>> templateEnum;
	protected List<CtEnumValue<?>> enumValuesDiff;
	protected boolean enumsEqual;
	
	public EnumsDiff(CtEnum<Enum<?>> solutionEnum, CtEnum<Enum<?>> templateEnum) {
		this.solutionEnum = solutionEnum;
		this.templateEnum = templateEnum;
		this.enumValuesDiff = generateEnumValuesDiff();
		this.enumsEqual = areEnumsEqual();
	}

    /**
     * This method generates the enum values diff of the solution and template enum, e.g. the enum values defined in the
     * solution enum but not in the template enum.
     * @return
     */
	private List<CtEnumValue<?>> generateEnumValuesDiff() {
        Predicate<CtEnumValue<?>> enumValueIsImplicit = CtElement::isImplicit;

        List<CtEnumValue<?>> enumValuesDiff = new ArrayList<>(solutionEnum.getEnumValues());
        enumValuesDiff.removeIf(enumValueIsImplicit);

        if(templateEnum != null) {
            List<CtEnumValue<?>> templateEnumValues = templateEnum.getEnumValues();
            templateEnumValues.removeIf(enumValueIsImplicit);

            for(CtEnumValue<?> templateEnumValue : templateEnumValues) {
                enumValuesDiff.removeIf(solutionEnumValue -> solutionEnumValue.getSimpleName().equals(templateEnumValue.getSimpleName()));
            }
        }

        return enumValuesDiff;
	}

    /**
     * This method checks if the solution enum is the same in structure as the template enum.
     * @return True, if the solution enum is the same in structure as the template enum, false otherwise.
     */
	private boolean areEnumsEqual() {
		return this.enumValuesDiff.isEmpty();
	}
	
}
