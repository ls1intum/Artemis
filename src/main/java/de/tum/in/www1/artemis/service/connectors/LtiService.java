package de.tum.in.www1.artemis.service.connectors;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.tuple.Pair;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

@Service
public class LtiService {

    public static final String TUMX = "TUMx";

    public static final String U4I = "U4I";

    protected static final List<SimpleGrantedAuthority> SIMPLE_USER_LIST_AUTHORITY = Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));

    private final Logger log = LoggerFactory.getLogger(LtiService.class);

    @Value("${artemis.lti.oauth-key:#{null}}")
    private Optional<String> OAUTH_KEY;

    @Value("${artemis.lti.oauth-secret:#{null}}")
    private Optional<String> OAUTH_SECRET;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    private Optional<String> USER_PREFIX_EDX;

    @Value("${artemis.lti.user-prefix-u4i:#{null}}")
    private Optional<String> USER_PREFIX_U4I;

    @Value("${artemis.lti.user-group-name-edx:#{null}}")
    private Optional<String> USER_GROUP_NAME_EDX;

    @Value("${artemis.lti.user-group-name-u4i:#{null}}")
    private Optional<String> USER_GROUP_NAME_U4I;

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    private final ResultRepository resultRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final LtiUserIdRepository ltiUserIdRepository;

    private final HttpClient client;

    // Ok, Spring actually injects the response for the current context using a proxy
    // TODO Although this works, this is a bad design practice and we should move all response related code to the controller
    private final HttpServletResponse response;

    public final Map<String, Pair<LtiLaunchRequestDTO, Exercise>> launchRequestForSession = new HashMap<>();

    public LtiService(UserCreationService userCreationService, UserRepository userRepository, LtiOutcomeUrlRepository ltiOutcomeUrlRepository, ResultRepository resultRepository,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, LtiUserIdRepository ltiUserIdRepository, HttpServletResponse response) {
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
        this.ltiOutcomeUrlRepository = ltiOutcomeUrlRepository;
        this.resultRepository = resultRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.ltiUserIdRepository = ltiUserIdRepository;
        this.response = response;
        this.client = HttpClientBuilder.create().build();
    }

    /**
     * Handles LTI launch requests.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     */
    public void handleLaunchRequest(LtiLaunchRequestDTO launchRequest, Exercise exercise) {

        // Authenticate the LTI user
        Optional<Authentication> auth = authenticateLtiUser(launchRequest);

        if (auth.isPresent()) {
            // Authentication was successful
            SecurityContextHolder.getContext().setAuthentication(auth.get());
            onSuccessfulLtiAuthentication(launchRequest, exercise);
        }
        else {

            /*
             * None of the auth methods were successful. -> Map the launchRequest to the Session ID -> If the user signs in manually later, we use it in
             * LtiAuthenticationSuccessListener
             */

            // Find (new) session ID
            String sessionId = null;
            if (response.containsHeader("Set-Cookie")) {
                for (String cookie : response.getHeaders("Set-Cookie")) {
                    if (cookie.contains("JSESSIONID")) {
                        Pattern pattern = Pattern.compile("=(.*?);");
                        Matcher matcher = pattern.matcher(response.getHeader("Set-Cookie"));
                        if (matcher.find()) {
                            sessionId = matcher.group(1);
                        }
                        break;
                    }
                }
            }
            if (sessionId == null) {
                WebAuthenticationDetails authDetails = (WebAuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();
                log.info("Remembering launchRequest for session ID {}", authDetails.getSessionId());
                sessionId = authDetails.getSessionId();
            }

            // Found it. Remember the launch request for later login.
            if (sessionId != null) {
                log.info("Remembering launchRequest for session ID {}", sessionId);
                launchRequestForSession.put(sessionId, Pair.of(launchRequest, exercise));
            }
        }
    }

    /**
     * Handler for successful LTI auth Saves the LTI outcome url and permanently maps the LTI user id to the user
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     */
    public void onSuccessfulLtiAuthentication(LtiLaunchRequestDTO launchRequest, Exercise exercise) {
        // Auth was successful
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Make sure user is added to group for this exercise
        addUserToExerciseGroup(user, exercise.getCourseViaExerciseGroupOrCourseMember());

        // Save LTI user ID to automatically sign in the next time
        saveLtiUserId(user, launchRequest.getUser_id());

        // Save LTI outcome url
        saveLtiOutcomeUrl(user, exercise, launchRequest.getLis_outcome_service_url(), launchRequest.getLis_result_sourcedid());
    }

    /**
     * Signs in the LTI user into the exercise app. Therefore it creates a user, if necessary.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @return the authentication based on the user who invoked the launch request
     * @throws ArtemisAuthenticationException if the user cannot be authenticated, this exception will be thrown
     * @throws AuthenticationException        internal exception of Spring that might be thrown as well
     */
    private Optional<Authentication> authenticateLtiUser(LtiLaunchRequestDTO launchRequest) throws ArtemisAuthenticationException, AuthenticationException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (SecurityUtils.isAuthenticated()) {
            // 1. Case: User is already signed in. We are done here.
            return Optional.of(auth);
        }

        // If the LTI launch is used from edX studio, edX sends dummy data. (id="student")
        // Catch this case here.
        if (launchRequest.getUser_id().equals("student")) {
            throw new InternalAuthenticationServiceException("Invalid username sent by launch request. Please do not launch the exercise from edX studio. Use 'Preview' instead.");
        }

        if (StringUtils.isEmpty(launchRequest.getLis_person_contact_email_primary())) {
            throw new InternalAuthenticationServiceException("No email address sent by launch request. Please make sure the user has an accessible email address.");
        }

        final String email = launchRequest.getLis_person_contact_email_primary();
        final String username = createUsernameFromLaunchRequest(launchRequest);
        final String fullname = launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id();

        // 2. Case: Existing mapping for LTI user id
        // Check if there is an existing mapping for the user ID
        final var optionalLtiUserId = ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id());
        if (optionalLtiUserId.isPresent()) {
            final var user = optionalLtiUserId.get().getUser();
            // Authenticate
            return Optional.of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY));
        }

        // 3. Case: Lookup user with the LTI email address. Sign in as this user.
        // Check if lookup by email is enabled
        if (launchRequest.getCustom_lookup_user_by_email()) {
            // check if a user with this email address exists
            final var usernameLookupByEmail = artemisAuthenticationProvider.getUsernameForEmail(email);
            if (usernameLookupByEmail.isPresent()) {
                return loginUserByEmail(launchRequest, usernameLookupByEmail.get(), email, fullname);
            }
        }

        // 4. Case: Create new user
        // Check if an existing user is required
        if (!launchRequest.getCustom_require_existing_user()) {
            return createNewUserFromLaunchRequest(launchRequest, email, username, fullname);
        }

        return Optional.empty();
    }

    @NotNull
    private Optional<Authentication> createNewUserFromLaunchRequest(LtiLaunchRequestDTO launchRequest, String email, String username, String fullname) {
        final var user = userRepository.findOneByLogin(username).orElseGet(() -> {
            final User newUser;
            final var groups = new HashSet<String>();
            if (TUMX.equals(launchRequest.getContext_label()) && USER_GROUP_NAME_EDX.isPresent()) {
                groups.add(USER_GROUP_NAME_EDX.get());
                newUser = userCreationService.createUser(username, null, groups, USER_GROUP_NAME_EDX.get(), fullname, email, null, null, Constants.DEFAULT_LANGUAGE, true);
            }
            else if (U4I.equals(launchRequest.getContext_label()) && USER_GROUP_NAME_U4I.isPresent()) {
                groups.add(USER_GROUP_NAME_U4I.get());
                newUser = userCreationService.createUser(username, null, groups, USER_GROUP_NAME_U4I.get(), fullname, email, null, null, Constants.DEFAULT_LANGUAGE, true);
            }
            else {
                String message = "User group not activated or unknown context_label sent in LTI Launch Request: " + launchRequest;
                log.error(message);
                throw new InternalAuthenticationServiceException(message);
            }
            newUser.setActivationKey(null);
            userRepository.save(newUser);
            log.info("Created new user {}", newUser);
            return newUser;
        });

        log.info("createNewUserFromLaunchRequest: {}", user);

        log.info("Signing in as {}", username);
        return Optional.of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), SIMPLE_USER_LIST_AUTHORITY));
    }

    private Optional<Authentication> loginUserByEmail(LtiLaunchRequestDTO launchRequest, String username, String email, String fullname) {
        log.info("Signing in as {}", username);
        var firstName = "";
        if (TUMX.equals(launchRequest.getContext_label()) && USER_GROUP_NAME_EDX.isPresent()) {
            firstName = USER_GROUP_NAME_EDX.get();
        }
        else if (U4I.equals(launchRequest.getContext_label()) && USER_GROUP_NAME_U4I.isPresent()) {
            firstName = USER_GROUP_NAME_U4I.get();
        }
        final var user = artemisAuthenticationProvider.getOrCreateUser(new UsernamePasswordAuthenticationToken(username, ""), firstName, fullname, email, true);

        return Optional.of(
                new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()))));
    }

    @NotNull
    private String createUsernameFromLaunchRequest(LtiLaunchRequestDTO launchRequest) {
        final String username;
        if (TUMX.equals(launchRequest.getContext_label()) && USER_GROUP_NAME_EDX.isPresent() && USER_PREFIX_EDX.isPresent()) {
            username = USER_PREFIX_EDX.get() + (launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id());
        }
        else if (U4I.equals(launchRequest.getContext_label()) && USER_GROUP_NAME_U4I.isPresent() && USER_PREFIX_U4I.isPresent()) {
            username = USER_PREFIX_U4I.get() + (launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id());
        }
        else {
            throw new InternalAuthenticationServiceException("Unknown context_label sent in LTI Launch Request: " + launchRequest);
        }

        return username;
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

            if (!user.getLogin().startsWith("edx")) {
                // try to sync with authentication service for actual users (not for edx users)
                try {
                    artemisAuthenticationProvider.addUserToGroup(user, courseStudentGroupName);
                }
                catch (ArtemisAuthenticationException e) {
                    // This might throw exceptions, for example if the group does not exist on the authentication service. We can safely ignore it
                }
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
    private void saveLtiOutcomeUrl(User user, Exercise exercise, String url, String sourcedId) {

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
     * Checks if a LTI request is correctly signed via OAuth with the secret
     *
     * @param request The request to check
     * @return null if the request is valid, otherwise an error message which indicates the reason why the verification failed
     */
    public String verifyRequest(HttpServletRequest request) {
        LtiVerifier ltiVerifier = new LtiOauthVerifier();
        if (this.OAUTH_SECRET.isEmpty()) {
            // this means Artemis does not support this
            String message = "verifyRequest for LTI is not supported on this Artemis instance, artemis.lti.oauth-secret was not specified in the yml configuration";
            log.warn(message);
            return message;
        }
        try {
            LtiVerificationResult ltiResult = ltiVerifier.verify(request, this.OAUTH_SECRET.get());
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
     * This method is pinged on new exercise results. It sends an message to the LTI consumer with the new score.
     *
     * @param participation The exercise participation for which a new build result is available
     */
    public void onNewResult(StudentParticipation participation) {
        if (this.OAUTH_KEY.isPresent() && this.OAUTH_SECRET.isPresent()) {
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
                        HttpPost request = IMSPOXRequest.buildReplaceResult(ltiOutcomeUrl.getUrl(), OAUTH_KEY.get(), OAUTH_SECRET.get(), ltiOutcomeUrl.getSourcedId(), score, null,
                                false);
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

    /**
     * Handle launch request which was initiated earlier by a LTI consumer
     *
     * @param sessionId The ID of the current user's session (JSESSIONID)
     */
    public void handleLaunchRequestForSession(String sessionId) {
        if (launchRequestForSession.containsKey(sessionId)) {

            log.info("Found LTI launchRequest for session ID {}", sessionId);

            LtiLaunchRequestDTO launchRequest = launchRequestForSession.get(sessionId).getLeft();
            Exercise exercise = launchRequestForSession.get(sessionId).getRight();

            onSuccessfulLtiAuthentication(launchRequest, exercise);

            // clean up
            launchRequestForSession.remove(sessionId);
        }
    }
}
