package de.tum.in.www1.artemis.domain.metis;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * A specific ConstraintValidator that validates if the Post context is mutually exclusive,
 * i.e. that either a Lecture, an Exercise, or a CourseWideContext is associated with the Post.
 * In case the Post is associated with a CourseWideContext, the according course has to be set as well.
 */
public class PostContextConstraintValidator implements ConstraintValidator<PostConstraints, Post> {

    @Override
    public boolean isValid(Post post, ConstraintValidatorContext ctx) {
        return (post.getExercise() != null && post.getLecture() == null && post.getPlagiarismCase() == null && post.getCourseWideContext() == null)
                || (post.getExercise() == null && post.getLecture() != null && post.getPlagiarismCase() == null && post.getCourseWideContext() == null)
                || (post.getExercise() == null && post.getLecture() == null && post.getPlagiarismCase() != null && post.getCourseWideContext() == null)
                || (post.getExercise() == null && post.getLecture() == null && post.getPlagiarismCase() == null && post.getCourseWideContext() != null && post.getCourse() != null);
    }
}
