package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.web.rest.dto.user.UserNameAndLoginDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathInformationDTO(long id, UserNameAndLoginDTO user, int progress) {

    public static LearningPathInformationDTO of(LearningPath learningPath) {
        return new LearningPathInformationDTO(learningPath.getId(), UserNameAndLoginDTO.of(learningPath.getUser()), learningPath.getProgress());
    }
}
