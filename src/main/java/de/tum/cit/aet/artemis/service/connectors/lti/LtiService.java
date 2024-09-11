package de.tum.cit.aet.artemis.service.connectors.lti;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.LtiEmailAlreadyInUseException;
import de.tum.cit.aet.artemis.core.security.ArtemisAuthenticationProvider;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.service.connectors.ci.CIUserManagementService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VcsUserManagementService;
import de.tum.cit.aet.artemis.service.user.UserCreationService;
import tech.jhipster.security.RandomUtil;

@Service
@Profile("lti")
public class LtiService {

    @Value("${artemis.lti.trustExternalLTISystems:false}")
    private boolean trustExternalLTISystems;

    public static final String LTI_GROUP_NAME = "lti";

    protected static final List<SimpleGrantedAuthority> SIMPLE_USER_LIST_AUTHORITY = Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));

    private static final Logger log = LoggerFactory.getLogger(LtiService.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final JWTCookieService jwtCookieService;

    private final Optional<VcsUserManagementService> optionalVcsUserManagementService;

    private final Optional<CIUserManagementService> optionalCIUserManagementService;

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, ArtemisAuthenticationProvider artemisAuthenticationProvider,
            JWTCookieService jwtCookieService, Optional<VcsUserManagementService> optionalVcsUserManagementService,
            Optional<CIUserManagementService> optionalCIUserManagementService) {
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.jwtCookieService = jwtCookieService;
        this.optionalVcsUserManagementService = optionalVcsUserManagementService;
        this.optionalCIUserManagementService = optionalCIUserManagementService;
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
                User user = userRepository.findUserWithGroupsAndAuthoritiesByEmail(email).orElseThrow();
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

    @NotNull
    protected Authentication createNewUserFromLaunchRequest(String email, String login, String firstName, String lastName) {
        final var user = userRepository.findOneByLogin(login).orElseGet(() -> {
            final User newUser;
            final var groups = new HashSet<String>();
            groups.add(LTI_GROUP_NAME);

            var password = RandomUtil.generatePassword();
            newUser = userCreationService.createUser(login, password, groups, firstName, lastName, email, null, null, Constants.DEFAULT_LANGUAGE, true);
            newUser.setActivationKey(null);
            userRepository.save(newUser);

            optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.createVcsUser(newUser, password));
            optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.createUser(newUser, password));

            log.info("Created new user {}", newUser);
            return newUser;

        });

        log.info("createNewUserFromLaunchRequest: {}", user);

        log.info("Signing in as {}", login);
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY);
    }

    /**
     * Handler for successful LTI auth. Adds the groups to the user
     *
     * @param user     The user that is authenticated
     * @param exercise Exercise to launch
     */
    public void onSuccessfulLtiAuthentication(User user, Exercise exercise) {
        // Make sure user is added to group for this exercise
        addUserToExerciseGroup(user, exercise.getCourseViaExerciseGroupOrCourseMember());
    }

    /**
     * Add a user to the course student group
     *
     * @param user   the user who should be added the course
     * @param course the course to which the user should be added
     */
    private void addUserToExerciseGroup(User user, Course course) {
        String courseStudentGroupName = course.getStudentGroupName();
        if (!user.getGroups().contains(courseStudentGroupName)) {
            Set<String> groups = user.getGroups();
            groups.add(courseStudentGroupName);
            user.setGroups(groups);
            userCreationService.saveUser(user);
        }
    }

    /**
     * Build the response for the LTI launch to include the necessary query params and the JWT cookie.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     * @param response             the response to add the JWT cookie to
     */
    public void buildLtiResponse(UriComponentsBuilder uriComponentsBuilder, HttpServletResponse response) {
        User user = userRepository.getUser();

        if (!user.getActivated()) {
            log.info("User is not activated. Adding initialize parameter to query.");
            uriComponentsBuilder.queryParam("initialize", "");
        }

        log.info("Add/Update JWT cookie so the user will be logged in.");
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(true);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    /**
     * Checks if a user was created as part of an LTI launch.
     *
     * @param user the user to check if
     * @return true if the user was created as part of an LTI launch
     */
    public boolean isLtiCreatedUser(User user) {
        return user.getGroups().contains(LTI_GROUP_NAME);
    }

    /**
     * Include logout JWT cookie to response.
     *
     * @param response the response to add the JWT cookie to
     */
    protected void prepareLogoutCookie(HttpServletResponse response) {
        ResponseCookie responseCookie = jwtCookieService.buildLogoutCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }
}
