package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;

/**
 * This diff extends the functionality of ClassesDiff and handles structural elements that are exclusive to enums:
 *  - Enum values.
 */
@JsonSerialize(using = EnumsDiffSerializer.class)
public class EnumsDiff extends ClassesDiff {

	protected List<CtEnumValue<?>> enumValues;
	protected boolean enumsEqual;
	
	public EnumsDiff(CtEnum<Enum<?>> solutionEnum, CtEnum<Enum<?>> templateEnum) {
		super(solutionEnum, templateEnum);
		this.enumValues = generateEnumValuesDiff(solutionEnum, templateEnum);
		this.enumsEqual = areEnumsEqual();
	}

    /**
     * This method generates the enum values diff of the solution and template enum, e.g. the enum values defined in the
     * solution enum but not in the template enum.
     * @param solutionEnum The enum present in the project
     * @param templateEnum
     * @return
     */
	private List<CtEnumValue<?>> generateEnumValuesDiff(CtEnum<Enum<?>> solutionEnum, CtEnum<Enum<?>> templateEnum) {
        Predicate<CtEnumValue<?>> enumValueIsImplicit = CtElement::isImplicit;

        List<CtEnumValue<?>> enumValuesDiff = new ArrayList<>(solutionEnum.getEnumValues());
        enumValuesDiff.removeIf(enumValueIsImplicit);

        if(templateEnum != null) {
            List<CtEnumValue<?>> templateEnumValues = templateEnum.getEnumValues();
            templateEnumValues.removeIf(enumValueIsImplicit);

            for(CtEnumValue<?> templateEnumValue : templateEnumValues) {
                enumValuesDiff.removeIf(solutionEnumValue ->
                    solutionEnumValue.getSimpleName().equals(templateEnumValue.getSimpleName()));
            }
        }

        return enumValuesDiff;
	}

    /**
     * This method checks if the solution enum is the same in structure as the template enum.
     * @return True, if the solution enum is the same in structure as the template enum, false otherwise.
     */
	private boolean areEnumsEqual() {
		return super.classesEqual
				&& this.enumValues.isEmpty();
	}
	
}
