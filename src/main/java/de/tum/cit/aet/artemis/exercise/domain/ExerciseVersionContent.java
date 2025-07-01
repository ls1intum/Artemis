package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record representing the content of an exercise version.
 * This is used to store the exercise data in a structured format.
 */
public record ExerciseVersionContent(
        // Basic exercise information
        @JsonProperty("short_name") String shortName,

        @JsonProperty("title") String title,

        @JsonProperty("problem_statement") String problemStatement,

        // Dates
        @JsonProperty("start_date") ZonedDateTime startDate,

        @JsonProperty("release_date") ZonedDateTime releaseDate,

        @JsonProperty("due_date") ZonedDateTime dueDate,

        // Points and difficulty
        @JsonProperty("max_points") Double maxPoints,

        @JsonProperty("bonus_points") Double bonusPoints,

        @JsonProperty("difficulty") DifficultyLevel difficulty,

        // Programming exercise specific fields
        @JsonProperty("template_commit_id") String templateCommitId,

        @JsonProperty("solution_commit_id") String solutionCommitId,

        @JsonProperty("tests_commit_id") String testsCommitId,

        // Quiz exercise specific fields
        @JsonProperty("is_open_for_practice") Boolean isOpenForPractice,

        @JsonProperty("randomize_question_order") Boolean randomizeQuestionOrder,

        @JsonProperty("allowed_number_of_attempts") Integer allowedNumberOfAttempts,

        @JsonProperty("duration") Integer duration) {

    /**
     * Creates a builder for ExerciseVersionContent.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ExerciseVersionContent.
     */
    public static class Builder {

        private String shortName;

        private String title;

        private String problemStatement;

        private ZonedDateTime startDate;

        private ZonedDateTime releaseDate;

        private ZonedDateTime dueDate;

        private Double maxPoints;

        private Double bonusPoints;

        private DifficultyLevel difficulty;

        /**
         * Programming exercise specific fields
         */
        private String templateCommitId;

        private String solutionCommitId;

        private String testsCommitId;

        /**
         * Quiz exercise specific fields
         */
        private Boolean isOpenForPractice;

        private Boolean randomizeQuestionOrder;

        private Integer allowedNumberOfAttempts;

        private Integer duration;

        public Builder shortName(String shortName) {
            this.shortName = shortName;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder problemStatement(String problemStatement) {
            this.problemStatement = problemStatement;
            return this;
        }

        public Builder startDate(ZonedDateTime startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder releaseDate(ZonedDateTime releaseDate) {
            this.releaseDate = releaseDate;
            return this;
        }

        public Builder dueDate(ZonedDateTime dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public Builder duration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public Builder maxPoints(Double maxPoints) {
            this.maxPoints = maxPoints;
            return this;
        }

        public Builder bonusPoints(Double bonusPoints) {
            this.bonusPoints = bonusPoints;
            return this;
        }

        public Builder difficulty(DifficultyLevel difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder templateCommitId(String templateCommitId) {
            this.templateCommitId = templateCommitId;
            return this;
        }

        public Builder solutionCommitId(String solutionCommitId) {
            this.solutionCommitId = solutionCommitId;
            return this;
        }

        public Builder testsCommitId(String testsCommitId) {
            this.testsCommitId = testsCommitId;
            return this;
        }

        public Builder isOpenForPractice(Boolean isOpenForPractice) {
            this.isOpenForPractice = isOpenForPractice;
            return this;
        }

        public Builder randomizeQuestionOrder(Boolean randomizeQuestionOrder) {
            this.randomizeQuestionOrder = randomizeQuestionOrder;
            return this;
        }

        public Builder allowedNumberOfAttempts(Integer allowedNumberOfAttempts) {
            this.allowedNumberOfAttempts = allowedNumberOfAttempts;
            return this;
        }

        public ExerciseVersionContent build() {
            return new ExerciseVersionContent(shortName, title, problemStatement, startDate, releaseDate, dueDate, maxPoints, bonusPoints, difficulty, templateCommitId,
                    solutionCommitId, testsCommitId, isOpenForPractice, randomizeQuestionOrder, allowedNumberOfAttempts, duration);
        }
    }
}
