package de.tum.cit.aet.artemis.account.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;

/**
 * Creates the users of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class AccountDemoApi implements AbstractApi {

    private final UserDemoService userDemoService;

    /**
     * Login of the demo instructor. Used as the idempotency key of {@link #createDemoInstructor()}, so it must stay stable.
     */
    public static final String DEMO_INSTRUCTOR_LOGIN = "demo_instructor";

    /**
     * Password of every demo user. These credentials are intentionally well known: the demo course only exists on demo and manual testing instances, which are activated through
     * the opt-in {@code demo} profile and must never run in production. The secret scanner is suppressed on this line only, because a published password is not a leaked one.
     */
    private static final String DEMO_PASSWORD = "demo1234"; // nosemgrep

    private static final Logger log = LoggerFactory.getLogger(AccountDemoApi.class);

    private final UserRepository userRepository;

    private final UserCreationService userCreationService;

    public AccountDemoApi(UserRepository userRepository, UserCreationService userCreationService) {
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
    }

    public User createDemoStudent() {
        return userDemoService.createDemoStudent();
    }

    public User createDemoInstructor() {
        return userDemoService.createDemoInstructor();
    }
}
