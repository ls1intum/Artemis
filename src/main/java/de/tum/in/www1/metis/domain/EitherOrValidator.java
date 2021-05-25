package de.tum.in.www1.metis.domain;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EitherOrValidator implements ConstraintValidator<EitherOr, RootPost> {

    @Override
    public boolean isValid(RootPost rootPost, ConstraintValidatorContext ctx) {
        return (rootPost.getExerciseContext() != null && rootPost.getLectureContext() == null && rootPost.getCourseContext() == null)
                || (rootPost.getExerciseContext() == null && rootPost.getLectureContext() != null && rootPost.getCourseContext() == null) || (rootPost.getExerciseContext() == null
                        && rootPost.getLectureContext() == null && rootPost.getCourseContext() != null && rootPost.getCourseWideContext() != null);
    }

}
