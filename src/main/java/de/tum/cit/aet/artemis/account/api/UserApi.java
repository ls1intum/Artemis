package de.tum.cit.aet.artemis.account.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.UserDemoService;

/**
 * API for user functionality that other modules need to access.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class UserApi extends AbstractAccountApi {

    private final UserDemoService userDemoService;

    public UserApi(UserDemoService userDemoService) {
        this.userDemoService = userDemoService;
    }

    public User createDemoStudent() {
        return userDemoService.createDemoStudent();
    }

    public User createDemoInstructor() {
        return userDemoService.createDemoInstructor();
    }
}
