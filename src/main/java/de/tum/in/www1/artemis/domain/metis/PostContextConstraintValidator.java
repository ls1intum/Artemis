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
        return plagiarismCasePost(post) || messagePost(post);
    }

    private boolean plagiarismCasePost(Post post) {
        return post.getPlagiarismCase() != null && post.getConversation() == null;
    }

    private static boolean messagePost(Post post) {
        return post.getConversation() != null && post.getPlagiarismCase() == null;
    }
}
