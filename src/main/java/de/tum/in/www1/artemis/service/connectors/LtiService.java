package de.tum.in.www1.artemis.service.connectors;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LtiUserId;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.user.UserCreationService;

@Service
public class LtiService {

    public static final String LTI_GROUP_NAME = "lti";

    protected static final List<SimpleGrantedAuthority> SIMPLE_USER_LIST_AUTHORITY = Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));

    private final Logger log = LoggerFactory.getLogger(LtiService.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final TokenProvider tokenProvider;

    private final LtiUserIdRepository ltiUserIdRepository;

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, ArtemisAuthenticationProvider artemisAuthenticationProvider,
            TokenProvider tokenProvider, LtiUserIdRepository ltiUserIdRepository) {
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.tokenProvider = tokenProvider;
        this.ltiUserIdRepository = ltiUserIdRepository;
    }

    /**
     * Signs in the LTI user into the exercise app. If necessary, it will create a user.
     *
     * @param email the user's email
     * @param userId the user's id in the external LMS
     * @param username the user's username if we create a new user
     * @param firstName the user's firstname if we create a new user
     * @param lastName the user's lastname if we create a new user
     * @param requireExistingUser false if it's not allowed to create new users
     * @param lookupUserByEmail false if it's not allowed to find existing users with the provided email
     * @throws InternalAuthenticationServiceException if no email is provided, this exception will be thrown
     * @throws ArtemisAuthenticationException if the user cannot be authenticated, this exception will be thrown
     * @throws AuthenticationException        internal Spring exception that might be thrown as well
     */
    public void authenticateLtiUser(String email, String userId, String username, String firstName, String lastName, boolean requireExistingUser, boolean lookupUserByEmail)
            throws ArtemisAuthenticationException, AuthenticationException {
        if (SecurityUtils.isAuthenticated()) {
            // 1. Case: User is already signed in. We are done here.
            return;
        }

        if (StringUtils.isEmpty(email)) {
            throw new InternalAuthenticationServiceException("No email address sent by launch request. Please make sure the user has an accessible email address.");
        }

        // 2. Case: Existing mapping for LTI user id
        final var optionalLtiUserId = ltiUserIdRepository.findByLtiUserId(userId);
        if (optionalLtiUserId.isPresent()) {
            final var user = optionalLtiUserId.get().getUser();
            // Authenticate
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY));
            return;
        }

        // 3. Case: Lookup user with the LTI email address and sign in as this user if lookup by email is enabled
        if (lookupUserByEmail) {
            // check if a user with this email address exists
            final var usernameLookupByEmail = artemisAuthenticationProvider.getUsernameForEmail(email);
            if (usernameLookupByEmail.isPresent()) {
                SecurityContextHolder.getContext().setAuthentication(loginUserByEmail(usernameLookupByEmail.get(), email));
                return;
            }
        }

        // 4. Case: Create new user if an existing user is not required
        if (!requireExistingUser) {
            SecurityContextHolder.getContext().setAuthentication(createNewUserFromLaunchRequest(email, username, firstName, lastName));
            return;
        }

        throw new InternalAuthenticationServiceException("Could not find existing user or create new LTI user."); // If user couldn't be authenticated, throw an error
    }

    private Authentication loginUserByEmail(String username, String email) {
        log.info("Signing in as {}", username);
        final var user = artemisAuthenticationProvider.getOrCreateUser(new UsernamePasswordAuthenticationToken(username, ""), null, null, email, true);
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY);
    }

    @NotNull
    private Authentication createNewUserFromLaunchRequest(String email, String username, String firstName, String lastName) {
        final var user = userRepository.findOneByLogin(username).orElseGet(() -> {
            final User newUser;
            final var groups = new HashSet<String>();
            groups.add(LTI_GROUP_NAME);
            newUser = userCreationService.createUser(username, null, groups, firstName, lastName, email, null, null, Constants.DEFAULT_LANGUAGE, true);
            newUser.setActivationKey(null);
            userRepository.save(newUser);
            log.info("Created new user {}", newUser);
            return newUser;
        });

        log.info("createNewUserFromLaunchRequest: {}", user);

        log.info("Signing in as {}", username);
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY);
    }

    /**
     * Handler for successful LTI auth. Maps the LTI user id to the user and adds the groups to the user
     *
     * @param user The user that is authenticated
     * @param userId The userId in the external LMS
     * @param exercise Exercise to launch
     */
    public void onSuccessfulLtiAuthentication(User user, String userId, Exercise exercise) {
        // Make sure user is added to group for this exercise
        addUserToExerciseGroup(user, exercise.getCourseViaExerciseGroupOrCourseMember());

        // Save LTI user ID to automatically sign in the next time
        saveLtiUserId(user, userId);
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

            // try to sync with authentication service
            try {
                artemisAuthenticationProvider.addUserToGroup(user, courseStudentGroupName);
            }
            catch (ArtemisAuthenticationException e) {
                // This might throw exceptions, for example if the group does not exist on the authentication service. We can safely ignore it
            }
        }
    }

    /**
     * Save the User <-> LTI User ID mapping
     *
     * @param user            the user that should be saved
     * @param ltiUserIdString the user id
     */
    private void saveLtiUserId(User user, String ltiUserIdString) {

        if (ltiUserIdString == null || ltiUserIdString.isEmpty()) {
            return;
        }

        LtiUserId ltiUserId = ltiUserIdRepository.findByUser(user).orElseGet(() -> {
            LtiUserId newltiUserId = new LtiUserId();
            newltiUserId.setUser(user);
            return newltiUserId;
        });
        ltiUserId.setLtiUserId(ltiUserIdString);
        ltiUserIdRepository.save(ltiUserId);
    }

    /**
     * Adds the necessary query params for an LTI launch.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     */
    public void addLtiQueryParams(UriComponentsBuilder uriComponentsBuilder) {
        User user = userRepository.getUser();

        if (!user.getActivated()) {
            uriComponentsBuilder.queryParam("initialize", "");
            String jwt = tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), true);
            log.debug("created jwt token: {}", jwt);
            uriComponentsBuilder.queryParam("jwt", jwt);
        }
        else {
            uriComponentsBuilder.queryParam("jwt", "");
            uriComponentsBuilder.queryParam("ltiSuccessLoginRequired", user.getLogin());
        }
    }
}
