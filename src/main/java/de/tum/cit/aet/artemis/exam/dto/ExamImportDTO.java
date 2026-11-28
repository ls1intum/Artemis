package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * DTO for importing exams with exercise groups and exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 * <p>
 * The exam settings are the {@link ExamUpdateDTO} embedded with {@link JsonUnwrapped}, so they stay flat in the JSON. An {@code id} in the
 * request is read and ignored: the import always creates a new exam.
 *
 * @param exam           the exam settings, validated with the constraints of {@link ExamUpdateDTO}
 * @param courseId       the id of the course to import into
 * @param exerciseGroups the exercise groups with the source exercises to copy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamImportDTO(@JsonUnwrapped @NotNull @Valid ExamUpdateDTO exam, @NotNull Long courseId, @Nullable List<@NotNull @Valid ExerciseGroupImportDTO> exerciseGroups)
        implements ExamSettingsDTO {

    /**
     * Creates an ExamImportDTO from an existing Exam entity.
     * Useful for tests and converting existing exams to the import format.
     *
     * @param exam     the exam to convert
     * @param courseId the target course ID
     * @return the DTO representation
     */
    public static ExamImportDTO of(Exam exam, Long courseId) {
        List<ExerciseGroupImportDTO> exerciseGroupDTOs = null;
        if (exam.getExerciseGroups() != null && !exam.getExerciseGroups().isEmpty()) {
            exerciseGroupDTOs = exam.getExerciseGroups().stream().map(ExerciseGroupImportDTO::of).toList();
        }

        return new ExamImportDTO(ExamUpdateDTO.of(exam).withoutId(), courseId, exerciseGroupDTOs);
    }

    /**
     * Creates a new Exam entity from this DTO.
     *
     * @param course the course to associate with the exam
     * @return a new Exam entity with exercise groups
     */
    public Exam toEntity(Course course) {
        Exam entity = new Exam();
        entity.setExamMode(exam.examMode());
        applySettingsTo(entity);
        entity.setCourse(course);

        // Add exercise groups with exercises
        if (exerciseGroups != null) {
            for (ExerciseGroupImportDTO groupDTO : exerciseGroups) {
                ExerciseGroup group = groupDTO.toEntity();
                entity.addExerciseGroup(group);
            }
        }

        return entity;
    }

    @Override
    public String title() {
        return exam.title();
    }

    @Override
    public boolean examWithAttendanceCheck() {
        return exam.examWithAttendanceCheck();
    }

    @Override
    public ZonedDateTime visibleDate() {
        return exam.visibleDate();
    }

    @Override
    public ZonedDateTime startDate() {
        return exam.startDate();
    }

    @Override
    public ZonedDateTime endDate() {
        return exam.endDate();
    }

    @Override
    public ZonedDateTime publishResultsDate() {
        return exam.publishResultsDate();
    }

    @Override
    public ZonedDateTime examStudentReviewStart() {
        return exam.examStudentReviewStart();
    }

    @Override
    public ZonedDateTime examStudentReviewEnd() {
        return exam.examStudentReviewEnd();
    }

    @Override
    public Integer gracePeriod() {
        return exam.gracePeriod();
    }

    @Override
    public int workingTime() {
        return exam.workingTime();
    }

    @Override
    public String startText() {
        return exam.startText();
    }

    @Override
    public String endText() {
        return exam.endText();
    }

    @Override
    public String confirmationStartText() {
        return exam.confirmationStartText();
    }

    @Override
    public String confirmationEndText() {
        return exam.confirmationEndText();
    }

    @Override
    public Integer examMaxPoints() {
        return exam.examMaxPoints();
    }

    @Override
    public Boolean randomizeExerciseOrder() {
        return exam.randomizeExerciseOrder();
    }

    @Override
    public Integer numberOfExercisesInExam() {
        return exam.numberOfExercisesInExam();
    }

    @Override
    public Integer numberOfCorrectionRoundsInExam() {
        return exam.numberOfCorrectionRoundsInExam();
    }

    @Override
    public String examiner() {
        return exam.examiner();
    }

    @Override
    public String moduleNumber() {
        return exam.moduleNumber();
    }

    @Override
    public String courseName() {
        return exam.courseName();
    }

    @Override
    public ZonedDateTime exampleSolutionPublicationDate() {
        return exam.exampleSolutionPublicationDate();
    }

    @Override
    public ZonedDateTime examSummaryPublicationDate() {
        return exam.examSummaryPublicationDate();
    }

    @Override
    public String channelName() {
        return exam.channelName();
    }

    /**
     * Gets the list of exercise groups or an empty list if null.
     *
     * @return the exercise groups or empty list
     */
    public List<ExerciseGroupImportDTO> exerciseGroupsOrEmpty() {
        return exerciseGroups != null ? exerciseGroups : new ArrayList<>();
    }
}
