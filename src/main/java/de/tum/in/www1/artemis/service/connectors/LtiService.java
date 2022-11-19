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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
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

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, ArtemisAuthenticationProvider artemisAuthenticationProvider,
            TokenProvider tokenProvider) {
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Signs in the LTI user into the exercise app. If necessary, it will create a user.
     *
     * @param email the user's email
     * @param username the user's username if we create a new user
     * @param firstName the user's firstname if we create a new user
     * @param lastName the user's lastname if we create a new user
     * @param requireExistingUser false if it's not allowed to create new users
     * @throws InternalAuthenticationServiceException if no email is provided, or if no user can be authenticated, this exception will be thrown
     */
    public void authenticateLtiUser(String email, String username, String firstName, String lastName, boolean requireExistingUser) throws InternalAuthenticationServiceException {

        if (StringUtils.isEmpty(email)) {
            throw new InternalAuthenticationServiceException("No email address sent by launch request. Please make sure the user has an accessible email address.");
        }

        if (SecurityUtils.isAuthenticated()) {
            User user = userRepository.getUser();
            if (email.equalsIgnoreCase(user.getEmail())) { // 1. Case: User is already signed in and email matches the one provided in the launch
                return;
            }
            else {
                SecurityContextHolder.getContext().setAuthentication(null); // User is signed in but email does not match, meaning launch is for a different user
            }
        }

        // 3. Case: Lookup user with the LTI email address and sign in as this user
        final var usernameLookupByEmail = artemisAuthenticationProvider.getUsernameForEmail(email);
        if (usernameLookupByEmail.isPresent()) {
            SecurityContextHolder.getContext().setAuthentication(loginUserByEmail(usernameLookupByEmail.get(), email));
            return;
        }

        // 3. Case: Create new user if an existing user is not required
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
     * Handler for successful LTI auth. Adds the groups to the user
     *
     * @param user The user that is authenticated
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

    /**
     * Checks if a user was created as part of an LTI launch.
     *
     * @param user the user to check if
     * @return true if the user was created as part of an LTI launch
     */
    public boolean isLtiCreatedUser(User user) {
        return user.getGroups().contains(LTI_GROUP_NAME);
    }
}
