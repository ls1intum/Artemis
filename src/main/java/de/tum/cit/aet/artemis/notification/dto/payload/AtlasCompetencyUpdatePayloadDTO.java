package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the Atlas competency update notification, sent after an automatic competency orchestration run.
 * <p>
 * Only scalars and strings, because each component is stored as one parameter row: the applied changes travel as one
 * markdown list rather than as a collection. The stored copy of {@code changesMarkdown} is truncated with every other
 * long parameter, which is harmless because only the e-mail renders it and the e-mail receives the full value.
 *
 * @param outcome         how the run ended: {@code COMPLETED}, {@code PARTIAL} or {@code FAILED}
 * @param exerciseCount   the number of changed exercises the run processed
 * @param appliedCount    the number of changes the run applied
 * @param createdCount    the number of competencies the run created
 * @param editedCount     the number of competencies the run edited
 * @param deletedCount    the number of competencies the run deleted
 * @param assignedCount   the number of links the run created or reweighted
 * @param unassignedCount the number of links the run removed
 * @param changesMarkdown the applied changes as a markdown list, empty when the run applied none
 * @param omittedCount    the number of applied changes left out of {@code changesMarkdown} to bound the e-mail
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasCompetencyUpdatePayloadDTO(String outcome, int exerciseCount, int appliedCount, int createdCount, int editedCount, int deletedCount, int assignedCount,
        int unassignedCount, String changesMarkdown, int omittedCount) implements CourseNotificationPayloadDTO {
}
