package de.tum.cit.aet.artemis.exercise.domain;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record representing the content of an exercise version.
 * This is used to store the exercise data in a structured format.
 */
public record ExerciseVersionMetadata(
        // Basic exercise information

        // Programming exercise specific fields
        @JsonProperty("template_commit_id") String templateCommitId,

        @JsonProperty("solution_commit_id") String solutionCommitId,

        @JsonProperty("tests_commit_id") String testsCommitId,

        @JsonProperty("auxiliary_commit_ids") Map<String, String> auxiliaryCommitIds) implements Serializable {

    /**
     * Custom equals method to handle ZonedDateTime comparison properly.
     * ZonedDateTime instances might be differently formatted but represent the same instant.
     *
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ExerciseVersionMetadata that = (ExerciseVersionMetadata) o;

        if (!Objects.equals(templateCommitId, that.templateCommitId))
            return false;
        if (!Objects.equals(solutionCommitId, that.solutionCommitId))
            return false;
        if (!Objects.equals(testsCommitId, that.testsCommitId))
            return false;
        if (!Objects.equals(auxiliaryCommitIds, that.auxiliaryCommitIds))
            return false;
        return true;
    }

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

        /**
         * Programming exercise specific fields
         */
        private String templateCommitId;

        private String solutionCommitId;

        private String testsCommitId;

        private Map<String, String> auxiliaryCommitIds;

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

        public Builder auxiliaryCommitIds(Map<String, String> auxiliaryCommitIds) {
            this.auxiliaryCommitIds = auxiliaryCommitIds;
            return this;
        }

        public ExerciseVersionMetadata build() {
            return new ExerciseVersionMetadata(templateCommitId, solutionCommitId, testsCommitId, auxiliaryCommitIds);
        }
    }
}
