package de.tum.in.www1.artemis.web.rest.dto.competency;

import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.web.rest.dto.user.UserNameAndLoginDTO;

public record LearningPathInformationDTO(long id, UserNameAndLoginDTO user, int progress) {

    public LearningPathInformationDTO(LearningPath learningPath) {
        this(learningPath.getId(), new UserNameAndLoginDTO(learningPath.getUser()), learningPath.getProgress());
    }
}
