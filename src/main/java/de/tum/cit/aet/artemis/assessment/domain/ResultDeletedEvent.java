package de.tum.cit.aet.artemis.assessment.domain;

/**
 * Spring application event published when a result is deleted via JPQL (bypassing JPA lifecycle callbacks).
 * This allows listeners to trigger participant score recalculation without creating a direct dependency
 * from ResultService to InstanceMessageSendService, which would cause a circular dependency.
 *
 * @param exerciseId    the id of the exercise
 * @param participantId the id of the participant (user or team)
 * @param resultId      the id of the deleted result
 */
public record ResultDeletedEvent(long exerciseId, long participantId, long resultId) {
}
