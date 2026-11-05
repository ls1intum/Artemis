package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricScale;

/**
 * One rubric rating of a student's presentation, as it is included in the personal data export.
 *
 * @param courseTitle       the title of the course the presentation belongs to
 * @param presentationTitle the title of the presentation
 * @param presentationDate  when the presentation took place
 * @param criterionName     the name of the rated criterion
 * @param weight            the weight of the criterion in percent
 * @param scale             the rating scale of the criterion
 * @param levelPosition     the position of the selected level, starting at 0 for the lowest
 * @param comment           the comment the instructors entered for this criterion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricRatingExportDTO(String courseTitle, String presentationTitle, ZonedDateTime presentationDate, String criterionName, int weight,
        PresentationRubricScale scale, int levelPosition, String comment) {
}
