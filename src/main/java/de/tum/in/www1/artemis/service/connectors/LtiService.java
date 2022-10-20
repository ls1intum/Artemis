package de.tum.in.www1.artemis.service.connectors;

import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.imsglobal.lti.launch.LtiOauthVerifier;
import org.imsglobal.lti.launch.LtiVerificationException;
import org.imsglobal.lti.launch.LtiVerificationResult;
import org.imsglobal.lti.launch.LtiVerifier;
import org.imsglobal.pox.IMSPOXRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.user.UserCreationService;

@Service
public class LtiService {

    public static final String LTI_GROUP_NAME = "lti";

    protected static final List<SimpleGrantedAuthority> SIMPLE_USER_LIST_AUTHORITY = Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));

    private final Logger log = LoggerFactory.getLogger(LtiService.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    private final ResultRepository resultRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final LtiUserIdRepository ltiUserIdRepository;

    private final HttpClient client;

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, LtiOutcomeUrlRepository ltiOutcomeUrlRepository, ResultRepository resultRepository,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, CourseRepository courseRepository, LtiUserIdRepository ltiUserIdRepository) {
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
        this.ltiOutcomeUrlRepository = ltiOutcomeUrlRepository;
        this.resultRepository = resultRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.courseRepository = courseRepository;
        this.ltiUserIdRepository = ltiUserIdRepository;
        this.client = HttpClientBuilder.create().build();
    }

    /**
     * Signs in the LTI user into the exercise app. If necessary, it will create a user.
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
     * @param exercise      Exercise to launch
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
     * Save the LTI outcome url
     *
     * @param user      the user for which the lti outcome url should be saved
     * @param exercise  the exercise
     * @param url       the service url given by the LTI request
     * @param sourcedId the sourcedId given by the LTI request
     */
    public void saveLtiOutcomeUrl(User user, Exercise exercise, String url, String sourcedId) {

        if (url == null || url.isEmpty()) {
            return;
        }

        LtiOutcomeUrl ltiOutcomeUrl = ltiOutcomeUrlRepository.findByUserAndExercise(user, exercise).orElseGet(() -> {
            LtiOutcomeUrl newLtiOutcomeUrl = new LtiOutcomeUrl();
            newLtiOutcomeUrl.setUser(user);
            newLtiOutcomeUrl.setExercise(exercise);
            return newLtiOutcomeUrl;
        });
        ltiOutcomeUrl.setUrl(url);
        ltiOutcomeUrl.setSourcedId(sourcedId);
        ltiOutcomeUrlRepository.save(ltiOutcomeUrl);
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
     * Checks if an LTI request is correctly signed via OAuth with the secret
     *
     * @param request The request to check
     * @param onlineCourseConfiguration The configuration containing the secret used to verify the request
     * @return null if the request is valid, otherwise an error message which indicates the reason why the verification failed
     */
    public String verifyRequest(HttpServletRequest request, OnlineCourseConfiguration onlineCourseConfiguration) {
        if (onlineCourseConfiguration == null) {
            String message = "verifyRequest for LTI is not supported for this course";
            log.warn(message);
            return message;
        }

        LtiVerifier ltiVerifier = new LtiOauthVerifier();
        try {
            LtiVerificationResult ltiResult = ltiVerifier.verify(request, onlineCourseConfiguration.getLtiSecret());
            if (!ltiResult.getSuccess()) {
                String requestString = httpServletRequestToString(request);
                final var message = "LTI signature verification failed with message: " + ltiResult.getMessage() + "; error: " + ltiResult.getError() + ", launch result: "
                        + ltiResult.getLtiLaunchResult();
                log.error(message);
                log.error("Request: {}", requestString);
                return message;
            }
        }
        catch (LtiVerificationException e) {
            log.error("Lti signature verification failed. ", e);
            return "Lti signature verification failed; " + e.getMessage();
        }
        // this is the success case
        log.info("LTI Oauth Request Verification successful");
        return null;
    }

    /**
     * helper method to print a request with all its elements
     *
     * @param request the http request
     * @return a string with all debug information
     */
    private String httpServletRequestToString(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("Request Method = [").append(request.getMethod()).append("], ");
        sb.append("Request URL Path = [").append(request.getRequestURL()).append("], ");

        String headers = Collections.list(request.getHeaderNames()).stream().map(headerName -> headerName + " : " + Collections.list(request.getHeaders(headerName)))
                .collect(Collectors.joining(", "));

        if (headers.isEmpty()) {
            sb.append("Request headers: NONE,");
        }
        else {
            sb.append("Request headers: [").append(headers).append("],");
        }

        String parameters = Collections.list(request.getParameterNames()).stream().map(p -> p + " : " + Arrays.asList(request.getParameterValues(p)))
                .collect(Collectors.joining(", "));

        if (parameters.isEmpty()) {
            sb.append("Request parameters: NONE.");
        }
        else {
            sb.append("Request parameters: [").append(parameters).append("].");
        }

        return sb.toString();
    }

    /**
     * This method is pinged on new exercise results. It sends a message to the LTI consumer with the new score.
     *
     * @param participation The exercise participation for which a new build result is available
     */
    public void onNewResult(StudentParticipation participation) {
        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
        OnlineCourseConfiguration onlineCourseConfiguration = course.getOnlineCourseConfiguration();

        if (onlineCourseConfiguration == null) {
            throw new IllegalStateException("Online course should have an online course configuration.");
        }

        // Get the LTI outcome URL
        var students = participation.getStudents();
        if (students != null) {
            students.forEach(student -> ltiOutcomeUrlRepository.findByUserAndExercise(student, participation.getExercise()).ifPresent(ltiOutcomeUrl -> {

                String score = "0.00";

                // Get the latest result
                Optional<Result> latestResult = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId());

                if (latestResult.isPresent() && latestResult.get().getScore() != null) {
                    // LTI scores needs to be formatted as String between "0.00" and "1.00"
                    score = String.format(Locale.ROOT, "%.2f", latestResult.get().getScore().floatValue() / 100);
                }

                try {
                    log.info("Reporting score {} for participation {} to LTI consumer with outcome URL {} using the source id {}", score, participation, ltiOutcomeUrl.getUrl(),
                            ltiOutcomeUrl.getSourcedId());
                    HttpPost request = IMSPOXRequest.buildReplaceResult(ltiOutcomeUrl.getUrl(), onlineCourseConfiguration.getLtiKey(), onlineCourseConfiguration.getLtiSecret(),
                            ltiOutcomeUrl.getSourcedId(), score, null, false);
                    HttpResponse response = client.execute(request);
                    String responseString = new BasicResponseHandler().handleResponse(response);
                    log.info("Response from LTI consumer: {}", responseString);
                }
                catch (Exception ex) {
                    log.error("Reporting to LTI consumer failed", ex);
                }
            }));
        }
    }
}
