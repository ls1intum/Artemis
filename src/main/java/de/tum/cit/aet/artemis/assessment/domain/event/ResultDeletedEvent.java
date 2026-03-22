package de.tum.cit.aet.artemis.assessment.domain.event;

/**
 * Domain event published when a result is about to be deleted via JPQL DELETE,
 * which bypasses JPA lifecycle callbacks ({@code @PreRemove} in ResultListener).
 * <p>
 * This event allows the participant score recalculation to be triggered without
 * introducing a circular dependency between ResultService and InstanceMessageSendService.
 *
 * @param exerciseId          the ID of the exercise the result belongs to
 * @param participantId       the ID of the participant (student/team) who owns the result
 * @param resultIdToBeDeleted the ID of the result being deleted
 */
public record ResultDeletedEvent(Long exerciseId, Long participantId, Long resultIdToBeDeleted) {
}
