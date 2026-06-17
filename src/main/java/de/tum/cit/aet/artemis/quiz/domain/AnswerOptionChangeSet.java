package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Set;

/**
 * Describes the semantic result of replacing the answer options of a multiple-choice question.
 */
public record AnswerOptionChangeSet(Set<Long> addedIds, Set<Long> updatedIds, Set<Long> deletedIds, boolean requiresRecalculation) {
}
