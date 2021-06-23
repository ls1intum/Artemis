package de.tum.in.www1.artemis.domain.metis;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * A specific ConstraintValidator that validates if the Reaction context mutually exclusive,
 * i.e. that either a Post, an AnswerPost is associated with the Reaction.
 */
public class ReactionConstraintValidator implements ConstraintValidator<ReactionConstraints, Reaction> {

    @Override
    public boolean isValid(Reaction reaction, ConstraintValidatorContext ctx) {
        return (reaction.getPost() != null && reaction.getAnswerPost() == null) || (reaction.getPost() == null && reaction.getAnswerPost() != null);
    }
}
