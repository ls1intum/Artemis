package de.tum.in.www1.artemis.domain.metis;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * A specific ConstraintValidator that validates if the Post context is mutually exclusive,
 * i.e. that either a Lecture, an Exercise, a CourseWideContext or a Conversation is associated with the Post.
 * In case the Post is associated with a CourseWideContext or Conversation, the according course has to be set as well.
 */
public class PostContextConstraintValidator implements ConstraintValidator<PostConstraints, Post> {

    @Override
    public boolean isValid(Post post, ConstraintValidatorContext ctx) {
        return exercisePost(post) || lecturePost(post) || courseWidePost(post) || plagiarismCasePost(post) || messagePost(post);
    }

    private static boolean courseWidePost(Post post) {
        return post.getExercise() == null && post.getLecture() == null && post.getCourseWideContext() != null && post.getCourse() != null && post.getPlagiarismCase() == null
                && post.getConversation() == null;
    }

    private static boolean lecturePost(Post post) {
        return post.getExercise() == null && post.getLecture() != null && post.getCourseWideContext() == null && post.getPlagiarismCase() == null && post.getConversation() == null;
    }

    private static boolean exercisePost(Post post) {
        return post.getExercise() != null && post.getLecture() == null && post.getCourseWideContext() == null && post.getPlagiarismCase() == null && post.getConversation() == null;
    }

    private boolean plagiarismCasePost(Post post) {
        return post.getExercise() == null && post.getLecture() == null && post.getPlagiarismCase() != null && post.getCourseWideContext() == null && post.getConversation() == null;
    }

    private static boolean messagePost(Post post) {
        return post.getConversation() != null && post.getCourse() == null && post.getExercise() == null && post.getLecture() == null && post.getCourseWideContext() == null
                && post.getPlagiarismCase() == null;
    }
}
