package de.tum.cit.aet.artemis.domain.metis;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * A specific ConstraintValidator that validates if the Post context is mutually exclusive,
 * i.e. a conversation or a plagiarism case is associated with the Post.
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
