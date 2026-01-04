package de.tum.cit.aet.artemis.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO containing the result of a course material import operation.
 *
 * @param exercisesImported      the number of exercises imported
 * @param lecturesImported       the number of lectures imported
 * @param examsImported          the number of exams imported
 * @param competenciesImported   the number of competencies imported
 * @param tutorialGroupsImported the number of tutorial groups imported
 * @param faqsImported           the number of FAQs imported
 * @param errors                 list of error messages for any failed imports
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseMaterialImportResultDTO(int exercisesImported, int lecturesImported, int examsImported, int competenciesImported, int tutorialGroupsImported, int faqsImported,
        List<String> errors) {
}
