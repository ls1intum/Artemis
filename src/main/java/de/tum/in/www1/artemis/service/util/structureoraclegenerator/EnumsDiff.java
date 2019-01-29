package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;

@JsonSerialize(using = EnumsDiffSerializer.class)
public class EnumsDiff extends ClassesDiff {

    private CtEnum<Enum<?>> solutionEnum;
    private CtEnum<Enum<?>> templateEnum;
	protected List<CtEnumValue<?>> enumValues;
	protected boolean enumsEqual;
	
	public EnumsDiff(CtEnum<Enum<?>> solutionEnum, CtEnum<Enum<?>> templateEnum) {
		super(solutionEnum, templateEnum);
		this.solutionEnum = solutionEnum;
		this.templateEnum = templateEnum;
		this.enumValues = generateEnumValuesDiff();
		this.enumsEqual = areEnumsEqual();
	}

	private List<CtEnumValue<?>> generateEnumValuesDiff() {
        Predicate<CtEnumValue<?>> enumValueIsImplicit = e -> e.isImplicit();

        List<CtEnumValue<?>> enumValuesDiff = solutionEnum.getEnumValues();
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
	
	private boolean areEnumsEqual() {
		return super.areClassesEqual()
				&& this.enumValues.isEmpty();
	}
	
}
