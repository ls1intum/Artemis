package de.tum.in.www1.artemis.service.metis.conversation.auth;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

@Service
public class OneToOneChatAuthorizationService extends ConversationAuthorizationService {

    protected OneToOneChatAuthorizationService(UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        super(userRepository, authorizationCheckService);
    }

    public void isAllowedToCreateOneToOneChat(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
    }

}
