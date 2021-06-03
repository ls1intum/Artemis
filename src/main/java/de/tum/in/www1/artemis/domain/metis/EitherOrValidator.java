package de.tum.in.www1.artemis.domain.metis;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EitherOrValidator implements ConstraintValidator<EitherOr, Post> {

    @Override
    public boolean isValid(Post post, ConstraintValidatorContext ctx) {
        return (post.getExercise() != null && post.getLecture() == null && post.getCourse() == null)
                || (post.getExercise() == null && post.getLecture() != null && post.getCourse() == null)
                || (post.getExercise() == null && post.getLecture() == null && post.getCourse() != null && post.getCourseWideContext() != null);
    }
}
