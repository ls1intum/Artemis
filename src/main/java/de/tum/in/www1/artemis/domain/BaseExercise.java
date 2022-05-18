package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.view.QuizView;

@MappedSuperclass
public abstract class BaseExercise extends DomainObject {

    @Column(name = "title")
    @JsonView(QuizView.Before.class)
    private String title;

    @Column(name = "short_name")
    @JsonView(QuizView.Before.class)
    private String shortName;

    @Column(name = "max_points")
    private Double maxPoints;

    @Column(name = "bonus_points")
    private Double bonusPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type")
    private AssessmentType assessmentType;

    @Column(name = "release_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime releaseDate;

    @Column(name = "due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime dueDate;

    @Column(name = "assessment_due_date")
    @JsonView(QuizView.Before.class)
    private ZonedDateTime assessmentDueDate;

    @Nullable
    @Column(name = "example_solution_publication_date")
    private ZonedDateTime exampleSolutionPublicationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    @JsonView(QuizView.Before.class)
    private DifficultyLevel difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private ExerciseMode mode;

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

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public void setAssessmentDueDate(ZonedDateTime assessmentDueDate) {
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

    /**
     * Checks if the assessment due date is in the past. Also returns true, if no assessment due date is set.
     *
     * @return true if the assessment due date is in the past, otherwise false
     */
    @JsonIgnore
    public boolean isAssessmentDueDateOver() {
        return this.assessmentDueDate == null || ZonedDateTime.now().isAfter(this.assessmentDueDate);
    }

    @Nullable
    public ZonedDateTime getExampleSolutionPublicationDate() {
        return exampleSolutionPublicationDate;
    }

    public void setExampleSolutionPublicationDate(@Nullable ZonedDateTime exampleSolutionPublicationDate) {
        this.exampleSolutionPublicationDate = exampleSolutionPublicationDate;
    }

    /**
     * Checks whether students should be able to see the example solution.
     *
     * @return true if example solution publication date is in the past, false otherwise (including null case).
     */
    public boolean isExampleSolutionPublished() {
        if (this.isExamExercise()) {
            // This feature is currently not available for exam exercises, this should return false
            // for exam exercises until the conditions for them is fully implemented.
            return false;
        }
        return this.exampleSolutionPublicationDate != null && ZonedDateTime.now().isAfter(this.exampleSolutionPublicationDate);
    }

    /**
     * check if students are allowed to see this exercise
     *
     * @return true, if students are allowed to see this exercise, otherwise false
     */
    @JsonView(QuizView.Before.class)
    public Boolean isVisibleToStudents() {
        if (releaseDate == null) {  // no release date means the exercise is visible to students
            return Boolean.TRUE;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    public abstract boolean isExamExercise();

    /**
     * This method is used to validate the assessmentDueDate of an exercise. An assessmentDueDate is valid if it is after the releaseDate and dueDate. A given assessmentDueDate is invalid without an according dueDate
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
     * @return true if the previousDate is valid
     */
    protected static boolean isNotAfterAndNotNull(ZonedDateTime previousDate, ZonedDateTime laterDate) {
        if (previousDate == null || laterDate == null) {
            return true;
        }
        return !previousDate.isAfter(laterDate);
    }
}
