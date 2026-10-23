package de.tum.cit.aet.artemis.account.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.api.dtos.DemoUsers;
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

    /**
     * Creates the demo users that do not exist yet, identified by their logins.
     *
     * @return all demo users with their authorities, whether they already existed or were created by this call.
     */
    public DemoUsers createDemoUsers() {
        return userDemoService.createDemoUsers();
    }
}
