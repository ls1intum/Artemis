package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathInformationDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserNameAndLoginDTO user, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int progress) {

    public static LearningPathInformationDTO of(LearningPath learningPath) {
        return new LearningPathInformationDTO(learningPath.getId(), UserNameAndLoginDTO.of(learningPath.getUser()), learningPath.getProgress());
    }
}
