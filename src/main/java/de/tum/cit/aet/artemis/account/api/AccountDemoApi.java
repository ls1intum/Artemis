package de.tum.cit.aet.artemis.account.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * Login of the demo student, the account visitors use to explore the student view. Used as the idempotency key of the user, so it must stay stable.
     */
    public static final String DEMO_STUDENT_LOGIN = "demo_student";

    /**
     * Login of the demo tutor. Used as the idempotency key of the user, so it must stay stable.
     */
    public static final String DEMO_TUTOR_LOGIN = "demo_tutor";

    /**
     * Login of the demo editor. Used as the idempotency key of the user, so it must stay stable.
     */
    public static final String DEMO_EDITOR_LOGIN = "demo_editor";

    /**
     * Login of the demo instructor. Used as the idempotency key of the user, so it must stay stable.
     */
    public static final String DEMO_INSTRUCTOR_LOGIN = "demo_instructor";

    /**
     * Prefix of the logins of the demo student's classmates, followed by their number starting at 1. Used as the idempotency key of the users, so it must stay stable.
     */
    public static final String DEMO_PEER_LOGIN_PREFIX = "demo_student_";

    /**
     * The fictional classmates of the demo student. Their submissions fill the statistics, the scores and the assessment queue of the demo course.
     */
    private static final List<DemoName> DEMO_PEER_NAMES = List.of(new DemoName("Amara", "Okafor"), new DemoName("Lukas", "Weber"), new DemoName("Sofia", "Rossi"),
            new DemoName("Hiroshi", "Tanaka"), new DemoName("Leila", "Haddad"), new DemoName("Mateo", "García"), new DemoName("Priya", "Sharma"), new DemoName("Jonas", "Berg"),
            new DemoName("Chloé", "Martin"), new DemoName("Kwame", "Mensah"));

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

    /**
     * Creates the demo users that do not exist yet, identified by their logins.
     *
     * @return all demo users, whether they already existed or were created by this call.
     */
    public DemoUsers createDemoUsers() {
        User student = createDemoUserIfMissing(DEMO_STUDENT_LOGIN, "Demo", "Student");
        User tutor = createDemoUserIfMissing(DEMO_TUTOR_LOGIN, "Demo", "Tutor");
        User editor = createDemoUserIfMissing(DEMO_EDITOR_LOGIN, "Demo", "Editor");
        User instructor = createDemoUserIfMissing(DEMO_INSTRUCTOR_LOGIN, "Demo", "Instructor");
        List<User> peers = IntStream.range(0, DEMO_PEER_NAMES.size())
                .mapToObj(index -> createDemoUserIfMissing(DEMO_PEER_LOGIN_PREFIX + (index + 1), DEMO_PEER_NAMES.get(index).firstName(), DEMO_PEER_NAMES.get(index).lastName()))
                .toList();
        return new DemoUsers(student, tutor, editor, instructor, peers);
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

    /**
     * The users of the demo course.
     *
     * @param student    the demo student, the account visitors use to explore the student view.
     * @param tutor      the demo tutor, who assesses submissions and leads the tutorial groups.
     * @param editor     the demo editor.
     * @param instructor the demo instructor, who owns the demo content.
     * @param peers      the fictional classmates of the demo student.
     */
    public record DemoUsers(User student, User tutor, User editor, User instructor, List<User> peers) {

        /**
         * @return the demo student followed by their classmates.
         */
        public List<User> students() {
            return Stream.concat(Stream.of(student), peers.stream()).toList();
        }
    }

    private record DemoName(String firstName, String lastName) {
    }
}
