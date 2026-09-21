package de.tum.cit.aet.artemis.lti.service;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.account.security.RandomUtil;
import de.tum.cit.aet.artemis.account.service.UserRecoveryKeyService;
import de.tum.cit.aet.artemis.account.service.user.AuthorityService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.exception.LtiEmailAlreadyInUseException;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.UserLti;
import de.tum.cit.aet.artemis.lti.repository.UserLtiRepository;

@Lazy
@Service
@Conditional(LtiEnabled.class)
public class LtiService {

    @Value("${artemis.lti.trustExternalLTISystems:false}")
    private boolean trustExternalLTISystems;

    protected static final List<SimpleGrantedAuthority> SIMPLE_USER_LIST_AUTHORITY = List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));

    private static final Logger log = LoggerFactory.getLogger(LtiService.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final UserCourseRoleRepository userCourseRoleRepository;

    private final AuthorityService authorityService;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final JWTCookieService jwtCookieService;

    private final UserLtiRepository userLtiRepository;

    private final UserRecoveryKeyService userRecoveryKeyService;

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, UserCourseRoleRepository userCourseRoleRepository, AuthorityService authorityService,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, JWTCookieService jwtCookieService, UserLtiRepository userLtiRepository,
            UserRecoveryKeyService userRecoveryKeyService) {
        this.userCreationService = userCreationService;
        this.userLtiRepository = userLtiRepository;
        this.userRecoveryKeyService = userRecoveryKeyService;
        this.userRepository = userRepository;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.authorityService = authorityService;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.jwtCookieService = jwtCookieService;
    }

    /**
     * Signs in the LTI user into the exercise app. If necessary, it will create a user.
     *
     * @param email               the user's email
     * @param username            the user's username if we create a new user
     * @param firstName           the user's firstname if we create a new user
     * @param lastName            the user's lastname if we create a new user
     * @param requireExistingUser false if it's not allowed to create new users
     * @throws InternalAuthenticationServiceException if no email is provided, or if no user can be authenticated, this exception will be thrown
     */
    public void authenticateLtiUser(String email, String username, String firstName, String lastName, boolean requireExistingUser) throws InternalAuthenticationServiceException {
        log.info("Authenticating LTI user with email: {}, username: {}, firstName: {}, lastName: {}, requireExistingUser: {}", email, username, firstName, lastName,
                requireExistingUser);
        if (!StringUtils.hasLength(email)) {
            log.warn("No email address sent by launch request. Please make sure the user has an accessible email address.");
            throw new InternalAuthenticationServiceException("No email address sent by launch request. Please make sure the user has an accessible email address.");
        }

        if (SecurityUtils.isAuthenticated()) {
            log.info("User is already signed in. Checking if email matches the one provided in the launch.");
            User user = userRepository.getUser();
            if (email.equalsIgnoreCase(user.getEmail())) { // 1. Case: User is already signed in and email matches the one provided in the launch
                log.info("User is already signed in and email matches the one provided in the launch. No further action required.");
                return;
            }
            else {
                log.info("User is already signed in but email does not match the one provided in the launch. Signing out user.");
                SecurityContextHolder.getContext().setAuthentication(null); // User is signed in but email does not match, meaning launch is for a different user
            }
        }

        // 2. Case: Lookup user with the LTI email address and make sure it's not in use
        if (artemisAuthenticationProvider.getUsernameForEmail(email).isPresent() || userRepository.findOneByEmailIgnoreCase(email).isPresent()) {
            log.info("User with email {} already exists. Email is already in use.", email);

            if (trustExternalLTISystems) {
                log.info("Trusting external LTI system. Authenticating user with email: {}", email);
                User user = userRepository.findOneWithAuthoritiesByEmailIgnoreCase(email).orElseThrow();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities()));
                return;
            }

            throw new LtiEmailAlreadyInUseException();
        }

        // 3. Case: Create new user if an existing user is not required
        if (!requireExistingUser) {
            log.info("Creating new user from launch request: {}, username: {}, firstName: {}, lastName: {}", email, username, firstName, lastName);
            SecurityContextHolder.getContext().setAuthentication(createNewUserFromLaunchRequest(email, username, firstName, lastName));
            return;
        }

        log.info("Could not find existing user or create new LTI user.");
        throw new InternalAuthenticationServiceException("Could not find existing user or create new LTI user."); // If user couldn't be authenticated, throw an error
    }

    @NonNull
    protected Authentication createNewUserFromLaunchRequest(String email, String login, String firstName, String lastName) {
        final var user = userRepository.findOneByLogin(login).orElseGet(() -> {
            var password = RandomUtil.generatePassword();
            final User newUser = userCreationService.createUser(login, password, firstName, lastName, email, null, null, Constants.DEFAULT_LANGUAGE, true);
            // Marked in user_lti rather than on the user row, which the lti module does not own. createUser already saved
            // the account, so the id is available and no further save of the user itself is needed.
            userLtiRepository.save(new UserLti(newUser.getId(), true));
            // createUser issues an activation key for every internal account. A launch-provisioned account never receives
            // the activation mail and must not be activatable by that key, so it is dropped again here - the account uses
            // `activated` as its own "already initialised" marker instead. See User#activated.
            userRecoveryKeyService.clearActivationKey(newUser.getId());

            log.info("Created new user {}", newUser);
            return newUser;

        });

        log.info("createNewUserFromLaunchRequest: {}", user);

        log.info("Signing in as {}", login);
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY);
    }

    /**
     * Handler for successful LTI auth. Enrolls the user as a student in the exercise's course.
     *
     * @param user     The user that is authenticated
     * @param exercise Exercise to launch
     */
    public void onSuccessfulLtiAuthentication(User user, Exercise exercise) {
        enrollUserInCourse(user, exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    /**
     * Enrolls a user as a student in the given course.
     *
     * @param user   the user to enroll
     * @param course the course to enroll the user in
     */
    private void enrollUserInCourse(User user, Course course) {
        if (!userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRole(user.getId(), course.getId(), CourseRole.STUDENT)) {
            userCourseRoleRepository.save(new UserCourseRole(user, course, CourseRole.STUDENT));
        }
        user = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        user.setAuthorities(authorityService.buildAuthorities(user));
        userCreationService.saveUser(user);
    }

    /**
     * Build the response for the LTI launch to include the necessary query params and the JWT cookie.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     * @param response             the response to add the JWT cookie to
     */
    public void buildLtiResponse(UriComponentsBuilder uriComponentsBuilder, HttpServletResponse response) {
        User user = userRepository.getUser();

        // Gated on the launch's own marker, not on `activated`: an account an administrator has deactivated is also
        // inactive, and offering it the initialisation dialog only leads to a request the endpoint refuses.
        if (needsInitialization(user)) {
            log.info("User has not completed the initialization from its first launch. Adding initialize parameter to query.");
            uriComponentsBuilder.queryParam("initialize", "");
        }

        log.info("Add/Update JWT cookie so the user will be logged in.");
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(true);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    /**
     * Whether the account was provisioned by a launch and still has to complete the initialisation that hands it its
     * password.
     *
     * @param user the account
     * @return true if the account still has to be initialised
     */
    public boolean needsInitialization(User user) {
        return user.getId() != null && userLtiRepository.existsByUserIdAndCreatedByLaunchIsTrueAndInitializedIsFalse(user.getId());
    }

    /**
     * Claims the one-time initialisation of a launch-provisioned account, so that only the first caller proceeds.
     *
     * @param user the account
     * @return true if this caller claimed it and may hand out a password
     */
    public boolean claimInitialization(User user) {
        return user.getId() != null && userLtiRepository.claimInitialization(user.getId()) == 1;
    }

    /**
     * Checks if a user was created as part of an LTI launch.
     *
     * @param user the user to check if
     * @return true if the user was created as part of an LTI launch
     */
    public boolean isLtiCreatedUser(User user) {
        // Null-checked because the marker is a row keyed on the user id, so an account that has not been persisted cannot
        // have one. Callers do pass transient users here, and looking one up by a null id would fail.
        return user.getId() != null && userLtiRepository.existsByUserIdAndCreatedByLaunchIsTrue(user.getId());
    }

    /**
     * Include logout JWT cookie to response.
     *
     * @param response the response to add the JWT cookie to
     */
    public void prepareLogoutCookie(HttpServletResponse response) {
        ResponseCookie responseCookie = jwtCookieService.buildLogoutCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }
}
