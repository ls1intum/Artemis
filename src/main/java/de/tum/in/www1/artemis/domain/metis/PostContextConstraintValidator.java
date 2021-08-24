package de.tum.in.www1.artemis.domain.metis;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * A specific ConstraintValidator that validates if the Post context is mutually exclusive,
 * i.e. that either a Lecture, an Exercise, or a CourseWideContext is associated with the Post.
 * In case the Post is associated with a course, there has to be a context specification in terms of a CourseWideContext.
 */
public class PostContextConstraintValidator implements ConstraintValidator<PostConstraints, Post> {

    @Override
    public boolean isValid(Post post, ConstraintValidatorContext ctx) {
        return (post.getExercise() != null && post.getLecture() == null && post.getCourseWideContext() == null)
                || (post.getExercise() == null && post.getLecture() != null && post.getCourseWideContext() == null)
                || (post.getExercise() == null && post.getLecture() == null && post.getCourseWideContext() != null);
    }
}
