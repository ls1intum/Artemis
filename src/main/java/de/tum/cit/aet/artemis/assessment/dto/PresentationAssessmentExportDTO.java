package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;

/**
 * One presentation assessment instance of a student, as it is included in the personal data export.
 *
 * @param courseTitle       the title of the course the presentation belongs to
 * @param presentationTitle the title of the presentation
 * @param gradingMethod     how the presentation is graded
 * @param maxPoints         the maximum points of the presentation, or null if it is not graded by score
 * @param presentationDate  when the presentation took place
 * @param resultPoints      the achieved points of a presentation graded by score, or null if it has none
 * @param passed            whether a presentation graded by pass/fail was passed, or null if it has no result
 * @param rubricScore       the score in percent of a presentation graded by rubric, or null if it has none
 * @param language          the language of the presentation
 * @param mode              whether the presentation is online or in person
 * @param location          the location of an in-person presentation
 * @param meetingLink       the meeting link of an online presentation
 * @param remark            the remark the instructors entered about the presentation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentExportDTO(String courseTitle, String presentationTitle, PresentationGradingMethod gradingMethod, Double maxPoints,
        ZonedDateTime presentationDate, Double resultPoints, Boolean passed, Double rubricScore, String language, PresentationAssessmentMode mode, String location,
        String meetingLink, String remark) {
}
