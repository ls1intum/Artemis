package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;

/**
 * One presentation assessment instance of a student, as it is included in the personal data export.
 *
 * @param courseTitle       the title of the course the presentation belongs to
 * @param presentationTitle the title of the presentation
 * @param maxPoints         the maximum points of the presentation
 * @param presentationDate  when the presentation took place
 * @param resultPoints      the achieved points, or null if the presentation has not been assessed yet
 * @param language          the language of the presentation
 * @param mode              whether the presentation is online or in person
 * @param location          the location of an in-person presentation
 * @param meetingLink       the meeting link of an online presentation
 * @param remark            the remark the instructors entered about the presentation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentExportDTO(String courseTitle, String presentationTitle, double maxPoints, ZonedDateTime presentationDate, Double resultPoints, String language,
        PresentationAssessmentMode mode, String location, String meetingLink, String remark) {
}
