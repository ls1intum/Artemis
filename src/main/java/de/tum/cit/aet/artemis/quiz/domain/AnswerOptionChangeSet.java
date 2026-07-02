package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Set;

/**
 * Describes the semantic result of replacing the answer options of a multiple-choice question.
 *
 * @param addedIds              newly allocated answer option IDs
 * @param updatedIds            preserved IDs whose persisted content changed
 * @param deletedIds            IDs removed from the JSON list
 * @param invalidIds            IDs kept in JSON as invalid historical components
 * @param requiresRecalculation whether scoring or statistics need to be recalculated
 */
public record AnswerOptionChangeSet(Set<Long> addedIds, Set<Long> updatedIds, Set<Long> deletedIds, Set<Long> invalidIds, boolean requiresRecalculation) {
}
