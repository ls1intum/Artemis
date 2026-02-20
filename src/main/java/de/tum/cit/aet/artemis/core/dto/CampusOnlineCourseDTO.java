package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a CAMPUSOnline course for the REST API.
 * Used for both search results and import/link responses.
 *
 * @param campusOnlineCourseId  the unique course identifier in CAMPUSOnline
 * @param title                 the course title from CAMPUSOnline
 * @param semester              the teaching term (e.g. "2025W" for winter semester 2025)
 * @param language              the course language (e.g. "DE", "EN"), may be null
 * @param responsibleInstructor the responsible instructor name, may be null
 * @param department            the department name, may be null
 * @param studyProgram          the study program name, may be null
 * @param alreadyImported       whether this course is already linked to an Artemis course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineCourseDTO(String campusOnlineCourseId, String title, String semester, String language, String responsibleInstructor, String department,
        String studyProgram, boolean alreadyImported) {
}
