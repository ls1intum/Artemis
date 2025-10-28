package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.util.StringUtil;

@MappedSuperclass
public abstract class BaseExercise extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "max_points", nullable = false)
    private Double maxPoints;

    @Column(name = "bonus_points")
    private Double bonusPoints = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    private AssessmentType assessmentType;

    @Column(name = "release_date")
    @Nullable
    private ZonedDateTime releaseDate;

    // TODO: Also use for quiz exercises
    @Column(name = "start_date")
    @Nullable
    private ZonedDateTime startDate;

    @Column(name = "due_date")
    @Nullable
    private ZonedDateTime dueDate;

    @Column(name = "assessment_due_date")
    @Nullable
    private ZonedDateTime assessmentDueDate;

    @Column(name = "example_solution_publication_date")
    @Nullable
    private ZonedDateTime exampleSolutionPublicationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", columnDefinition = "varchar(255) default 'INDIVIDUAL'", nullable = false)
    private ExerciseMode mode = ExerciseMode.INDIVIDUAL;

    public String getTitle() {
        return title;
    }

    /**
     * Sets the title of the exercise
     * all consecutive, trailing or preceding whitespaces are replaced with a single space.
     *
     * @param title the new (non-sanitized) title to be set
     */
    public void setTitle(String title) {
        this.title = title != null ? title.strip().replaceAll("\\s+", " ") : null;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Double maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Double getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(Double bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    @Nullable
    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(@Nullable ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    @Nullable
    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    @Nullable
    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(@Nullable ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    @Nullable
    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public void setAssessmentDueDate(@Nullable ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(DifficultyLevel difficulty) {
        this.difficulty = difficulty;
    }

    public ExerciseMode getMode() {
        return mode;
    }

    public void setMode(ExerciseMode mode) {
        this.mode = mode;
    }

    public boolean isTeamMode() {
        return mode == ExerciseMode.TEAM;
    }

    @Nullable
    public ZonedDateTime getExampleSolutionPublicationDate() {
        return exampleSolutionPublicationDate;
    }

    public void setExampleSolutionPublicationDate(@Nullable ZonedDateTime exampleSolutionPublicationDate) {
        this.exampleSolutionPublicationDate = exampleSolutionPublicationDate;
    }

    /**
     * Check if students are allowed to see this course exercise
     * IMPORTANT: this method won't work for exam exercises: they have a different visibility logic! We return false for exam exercises to avoid confusion!
     * For exam exercises, instead use ExamAccessService and/or ExamDateService
     *
     * @return true, if students are allowed to see this exercise, otherwise false
     */
    public boolean isVisibleToStudents() {
        if (isExamExercise()) {
            return false;
        }
        if (releaseDate == null) {  // no release date means the exercise is visible to students for course exercises
            return true;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    public abstract boolean isExamExercise();

    /**
     * This method is used to validate the assessmentDueDate of an exercise. An assessmentDueDate is valid if it is after the releaseDate and dueDate. A given assessmentDueDate is
     * invalid without an according dueDate
     *
     * @return true if there is no assessmentDueDateError
     */
    protected static boolean isValidAssessmentDueDate(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        if (assessmentDueDate == null) {
            return true;
        }
        // There cannot be a assessmentDueDate without dueDate
        if (dueDate == null) {
            return false;
        }
        return isNotAfterAndNotNull(dueDate, assessmentDueDate) && isNotAfterAndNotNull(releaseDate, assessmentDueDate);
    }

    /**
     * This method is used to validate the exampleSolutionPublicationDate of an exercise. An exampleSolutionPublicationDate is valid if it is after the releaseDate and dueDate.
     * Any given exampleSolutionPublicationDate is valid if releaseDate and dueDate are not set.
     * exampleSolutionPublicationDate is valid if it is not set.
     *
     * @return true if there is no exampleSolutionPublicationDateError
     */
    protected static boolean isValidExampleSolutionPublicationDate(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime exampleSolutionPublicationDate,
            IncludedInOverallScore includedInOverallScore) {
        if (exampleSolutionPublicationDate == null) {
            return true;
        }

        return (isNotAfterAndNotNull(dueDate, exampleSolutionPublicationDate) || includedInOverallScore == IncludedInOverallScore.NOT_INCLUDED)
                && isNotAfterAndNotNull(releaseDate, exampleSolutionPublicationDate);
    }

    /**
     * This method is used to validate if the previousDate is before the laterDate.
     *
     * @return true if the previousDate is valid
     */
    protected static boolean isNotAfterAndNotNull(ZonedDateTime previousDate, ZonedDateTime laterDate) {
        if (previousDate == null || laterDate == null) {
            return true;
        }
        return !previousDate.isAfter(laterDate);
    }

    /**
     * a helper method to get the exercise title in a sanitized form (i.e. usable in file names)
     * exercise abc?+# -> exercise_abc
     *
     * @return the sanitized exercise title
     **/
    @JsonIgnore
    public String getSanitizedExerciseTitle() {
        if (title == null) {
            return "exercise";
        }
        return StringUtil.sanitizeStringForFileName(title);
    }

    /**
     * helper method to get an exercise title for use in notifications, i.e. not null or empty
     *
     * @return the sanitized exercise title
     **/
    @JsonIgnore
    public String getExerciseNotificationTitle() {
        if (title == null || title.isBlank()) {
            return "unnamed exercise";
        }
        return title;
    }
}
