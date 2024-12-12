package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathDTO(long id, boolean startedByStudent, int progress) {

    public static LearningPathDTO of(LearningPath learningPath) {
        return new LearningPathDTO(learningPath.getId(), learningPath.isStartedByStudent(), learningPath.getProgress());
    }
}
