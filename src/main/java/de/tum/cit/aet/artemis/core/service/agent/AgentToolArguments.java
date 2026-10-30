package de.tum.cit.aet.artemis.core.service.agent;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyRequestDTO;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.programming.dto.CreateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;

/**
 * The arguments of the tools, one type per tool shape. Each is read from what the agent sent, so that a number is a number and a
 * list of competencies is a list of the DTO the endpoint takes. A missing argument is null and is reported by the tool that needs it.
 */
public final class AgentToolArguments {

    private AgentToolArguments() {
    }

    /**
     * A tool that needs no arguments.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoArguments() {
    }

    /**
     * A tool about one course.
     *
     * @param courseId the course
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourseArguments(Long courseId) {
    }

    /**
     * A tool about one competency.
     *
     * @param courseId     the course
     * @param competencyId the competency
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompetencyArguments(Long courseId, Long competencyId) {
    }

    /**
     * Creating competencies.
     *
     * @param courseId     the course
     * @param competencies the competencies to create
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateCompetenciesArguments(Long courseId, List<CourseCompetencyRequestDTO> competencies) {
    }

    /**
     * Changing a competency.
     *
     * @param courseId   the course
     * @param competency the whole competency, with its id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateCompetencyArguments(Long courseId, CourseCompetencyRequestDTO competency) {
    }

    /**
     * Importing standardized competencies.
     *
     * @param courseId     the course
     * @param catalogueIds the entries of the catalogue
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImportCompetenciesArguments(Long courseId, List<Long> catalogueIds) {
    }

    /**
     * Adding a relation to the competency graph.
     *
     * @param courseId         the course
     * @param tailCompetencyId the competency that assumes, extends or matches the head
     * @param headCompetencyId the other competency
     * @param relationType     how the two relate
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateRelationArguments(Long courseId, Long tailCompetencyId, Long headCompetencyId, RelationType relationType) {
    }

    /**
     * Changing the type of a relation.
     *
     * @param courseId     the course
     * @param relationId   the relation
     * @param relationType the new type
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangeRelationArguments(Long courseId, Long relationId, RelationType relationType) {
    }

    /**
     * Posting an announcement.
     *
     * @param courseId  the course
     * @param title     an optional headline
     * @param content   the text, in Markdown
     * @param channelId the announcement channel, if the course has several
     * @param confirm   whether the instructor approved this exact text
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostAnnouncementArguments(Long courseId, String title, String content, Long channelId, Boolean confirm) {
    }

    /**
     * Listing the exercises of a course.
     *
     * @param courseId the course
     * @param type     the kind of exercise
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListExercisesArguments(Long courseId, AgentExerciseType type) {
    }

    /**
     * Reading one exercise.
     *
     * @param type       the kind of exercise
     * @param exerciseId the exercise
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExerciseArguments(AgentExerciseType type, Long exerciseId) {
    }

    /**
     * Creating or changing a text exercise.
     *
     * @param exercise the exercise as the REST API takes it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextExerciseArguments(UpdateTextExerciseDTO exercise) {
    }

    /**
     * Creating or changing a modeling exercise.
     *
     * @param exercise the exercise as the REST API takes it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelingExerciseArguments(UpdateModelingExerciseDTO exercise) {
    }

    /**
     * Creating a programming exercise.
     *
     * @param exercise the exercise as the REST API takes it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateProgrammingExerciseArguments(CreateProgrammingExerciseDTO exercise) {
    }

    /**
     * Changing a programming exercise.
     *
     * @param exercise the exercise as the REST API takes it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateProgrammingExerciseArguments(UpdateProgrammingExerciseDTO exercise) {
    }

    /**
     * A tool about one programming exercise.
     *
     * @param exerciseId the exercise
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProgrammingExerciseArguments(Long exerciseId) {
    }

    /**
     * Replacing the problem statement of a programming exercise.
     *
     * @param exerciseId       the exercise
     * @param problemStatement the new problem statement, in Markdown
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProblemStatementArguments(Long exerciseId, String problemStatement) {
    }

    /**
     * Changing the dates of a programming exercise.
     *
     * @param timeline the exercise and its new dates
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelineArguments(ProgrammingExerciseTimelineUpdateDTO timeline) {
    }

    /**
     * Changing the weights and the visibility of test cases.
     *
     * @param exerciseId the exercise
     * @param testCases  the test cases, by id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestCaseWeightsArguments(Long exerciseId, List<ProgrammingExerciseTestCaseDTO> testCases) {
    }

    /**
     * Creating a lecture.
     *
     * @param courseId    the course
     * @param title       the title
     * @param description what it is about
     * @param startDate   when it starts
     * @param endDate     when it ends
     * @param confirm     whether the instructor approved this title and description
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateLectureArguments(Long courseId, String title, String description, ZonedDateTime startDate, ZonedDateTime endDate, Boolean confirm) {
    }

    /**
     * A tool about one lecture.
     *
     * @param lectureId the lecture
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LectureArguments(Long lectureId) {
    }
}
