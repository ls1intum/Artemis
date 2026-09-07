package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The course overview tabs that are available to a user, used to decide which sidebar entries to render and whether a
 * tab may be opened, without loading the (expensive) course content.
 * <p>
 * Each flag is computed from a cheap indexed {@code exists}/{@code count} query or a course column. The exercises tab is
 * always available, so it has no flag.
 *
 * @param lectures       whether the course has at least one lecture
 * @param exams          whether at least one exam of the course is already visible to the user
 * @param competencies   whether the course has at least one competency or prerequisite
 * @param tutorialGroups whether the course has at least one tutorial group
 * @param iris           whether the Iris course chat is enabled for the course
 * @param faq            whether the course has at least one accepted FAQ
 * @param learningPaths  whether learning paths are enabled for the course
 * @param communication  whether communication is enabled for the course
 * @param training       whether the course has quiz questions available for practice
 */
// ALWAYS rather than NON_EMPTY/NON_DEFAULT: every flag is meaningful, and omitting the false ones would force the
// client to distinguish "not available" from "not sent".
// NON_EMPTY per the project-wide DTO convention (enforced by ArchitectureTest#testJsonIncludeNonEmpty). Note it does
// not suppress anything here: Jackson treats only null, absent, empty collections and empty strings as empty, so a
// primitive `false` is still serialised (that would be NON_DEFAULT). All nine flags are therefore always present,
// which is what the client model relies on.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAvailableTabsDTO(boolean lectures, boolean exams, boolean competencies, boolean tutorialGroups, boolean iris, boolean faq, boolean learningPaths,
        boolean communication, boolean training) {
}
