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

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, UserCourseRoleRepository userCourseRoleRepository, AuthorityService authorityService,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, JWTCookieService jwtCookieService) {
        this.userCreationService = userCreationService;
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
                ensureAccountIsUsable(user);
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
                User user = userRepository.findOneWithAuthoritiesByEmail(email).orElseThrow();
                ensureAccountIsUsable(user);
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
            newUser.setLtiCreated(true);
            // An LTI user is provisioned by the launch, not by self-registration: they never receive an activation mail and so can never redeem an activation
            // key. State it explicitly here rather than relying on the factory default, which does create an unactivated user on an instance that has
            // self-registration enabled. Clearing the key alone used to leave the account stuck at activated = false with no way to ever activate it.
            newUser.setActivated(true);
            newUser.setActivationKey(null);
            userRepository.save(newUser);

            log.info("Created new user {}", newUser);
            return newUser;

        });

        // Covers the account that already existed under this login. A user this method just created is activated by construction.
        ensureAccountIsUsable(user);

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
     * <p>
     * Adds {@code ?initialize} while the account still needs to be shown the Artemis password generated for it, which the
     * client turns into the initialisation dialog. Keyed on {@link User#isLtiInitialized()} rather than on
     * {@link User#getActivated()}: an LTI account is provisioned by this launch rather than registered by its owner, so it
     * is created ready to use and the flag would never be false.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     * @param response             the response to add the JWT cookie to
     */
    public void buildLtiResponse(UriComponentsBuilder uriComponentsBuilder, HttpServletResponse response) {
        User user = userRepository.getUser();

        // Keyed on the LTI initialisation marker rather than on `activated`, which the account now always has: an LTI
        // account is provisioned by this launch rather than registered by its owner, so it is created ready to use.
        if (user.isLtiCreated() && !user.isLtiInitialized()) {
            log.info("LTI user has not been initialized yet. Adding initialize parameter to query.");
            uriComponentsBuilder.queryParam("initialize", "");
        }

        log.info("Add/Update JWT cookie so the user will be logged in.");
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(true);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    /**
     * Refuses a launch for an account that may not authenticate, so that a launch cannot become a way around an
     * administrator's decision.
     * <p>
     * The launch establishes a session by writing the security context directly instead of going through an
     * {@link org.springframework.security.authentication.AuthenticationProvider}, so it does not inherit the account-state
     * check that the internal, SAML2, OIDC and passkey providers each perform, and that both git paths perform. Without this
     * a deactivated or soft-deleted account could still be signed in through the LMS.
     *
     * @param user the account the launch resolved to
     * @throws InternalAuthenticationServiceException if the account is deactivated or soft-deleted
     */
    private void ensureAccountIsUsable(User user) {
        if (!user.getActivated() || user.isDeleted()) {
            log.warn("LTI launch for user {} whose account is deactivated or deleted", user.getLogin());
            throw new InternalAuthenticationServiceException("Account is not active");
        }
    }

    /**
     * Checks if a user was created as part of an LTI launch.
     *
     * @param user the user to check if
     * @return true if the user was created as part of an LTI launch
     */
    public boolean isLtiCreatedUser(User user) {
        return user.isLtiCreated();
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
