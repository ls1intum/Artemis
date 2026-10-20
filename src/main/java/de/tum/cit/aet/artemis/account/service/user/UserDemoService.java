package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

/**
 * Seeds the demo users of the {@code demo} profile, see {@code DemoDataSeedingService}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserDemoService {

    /**
     * Login of the demo student. Used as the idempotency key of {@link #createDemoStudent()}, so it must stay stable.
     */
    public static final String DEMO_STUDENT_LOGIN = "demo_student";

    /**
     * Login of the demo instructor. Used as the idempotency key of {@link #createDemoInstructor()}, so it must stay stable.
     */
    public static final String DEMO_INSTRUCTOR_LOGIN = "demo_instructor";

    /**
     * Password of every demo user. These credentials are intentionally well known: the demo course only exists on demo and manual testing instances, which are activated through
     * the opt-in {@code demo} profile and must never run in production. The secret scanner is suppressed on this line only, because a published password is not a leaked one.
     */
    private static final String DEMO_PASSWORD = "demo1234"; // nosemgrep

    private static final Logger log = LoggerFactory.getLogger(UserDemoService.class);

    private final UserRepository userRepository;

    private final UserCreationService userCreationService;

    public UserDemoService(UserRepository userRepository, UserCreationService userCreationService) {
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
    }

    /**
     * Creates the demo student if it does not exist yet, identified by {@link #DEMO_STUDENT_LOGIN}.
     *
     * @return the demo student, whether it already existed or was created by this call.
     */
    public User createDemoStudent() {
        return createDemoUserIfMissing(DEMO_STUDENT_LOGIN, "Demo", "Student");
    }

    /**
     * Creates the demo instructor if it does not exist yet, identified by {@link #DEMO_INSTRUCTOR_LOGIN}.
     *
     * @return the demo instructor, whether it already existed or was created by this call.
     */
    public User createDemoInstructor() {
        return createDemoUserIfMissing(DEMO_INSTRUCTOR_LOGIN, "Demo", "Instructor");
    }

    private User createDemoUserIfMissing(String login, String firstName, String lastName) {
        return userRepository.findOneByLogin(login).orElseGet(() -> {
            User user = userCreationService.createUser(login, DEMO_PASSWORD, firstName, lastName, login + "@artemis.local", null, null, "en", true);
            // createUser leaves an internal user deactivated with an activation key, because the regular registration flow activates it via that key, which no one is going to
            // redeem for a demo user. Activate it the way an administrator would, which also discards the key.
            userCreationService.activateUser(user);
            log.info("Created demo user '{}'", login);
            return user;
        });
    }
}
