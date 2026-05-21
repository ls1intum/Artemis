package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO containing options for importing course material from one course to another.
 *
 * @param sourceCourseId       the ID of the course to import from
 * @param importExercises      whether to import all exercises
 * @param importLectures       whether to import all lectures
 * @param importExams          whether to import all exams
 * @param importCompetencies   whether to import all competencies
 * @param importTutorialGroups whether to import tutorial group configuration
 * @param importFaqs           whether to import all FAQs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseMaterialImportOptionsDTO(long sourceCourseId, boolean importExercises, boolean importLectures, boolean importExams, boolean importCompetencies,
        boolean importTutorialGroups, boolean importFaqs) {
}
