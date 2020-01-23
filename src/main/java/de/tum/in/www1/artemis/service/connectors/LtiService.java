package de.tum.in.www1.artemis.service.connectors;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.imsglobal.lti.launch.LtiOauthVerifier;
import org.imsglobal.lti.launch.LtiVerificationException;
import org.imsglobal.lti.launch.LtiVerificationResult;
import org.imsglobal.lti.launch.LtiVerifier;
import org.imsglobal.pox.IMSPOXRequest;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.dto.LtiLaunchRequestDTO;

@Service
@Transactional
public class LtiService {

    public static final String TUMX = "TUMx";

    public static final String U4I = "U4I";

    private final Logger log = LoggerFactory.getLogger(LtiService.class);

    @Value("${artemis.lti.oauth-key}")
    private String OAUTH_KEY;

    @Value("${artemis.lti.oauth-secret}")
    private String OAUTH_SECRET;

    @Value("${artemis.lti.user-prefix-edx}")
    private String USER_PREFIX_EDX = "edx";

    @Value("${artemis.lti.user-prefix-u4i}")
    private String USER_PREFIX_U4I = "u4i";

    @Value("${artemis.lti.user-group-name-edx}")
    private String USER_GROUP_NAME_EDX = "edx";

    @Value("${artemis.lti.user-group-name-u4i}")
    private String USER_GROUP_NAME_U4I = "u4i";

    private final UserService userService;

    private final UserRepository userRepository;

    private final LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    private final ResultRepository resultRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final LtiUserIdRepository ltiUserIdRepository;

    // Ok, Spring actually injects the response for the current context using a proxy
    // TODO Although this works, this is a bad design practice and we should move all response related code to the controller
    private final HttpServletResponse response;

    public final HashMap<String, Pair<LtiLaunchRequestDTO, Exercise>> launchRequestForSession = new HashMap<>();

    public LtiService(UserService userService, UserRepository userRepository, LtiOutcomeUrlRepository ltiOutcomeUrlRepository, ResultRepository resultRepository,
            ArtemisAuthenticationProvider artemisAuthenticationProvider, LtiUserIdRepository ltiUserIdRepository, HttpServletResponse response) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.ltiOutcomeUrlRepository = ltiOutcomeUrlRepository;
        this.resultRepository = resultRepository;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.ltiUserIdRepository = ltiUserIdRepository;
        this.response = response;
    }

    /**
     * Handles LTI launch requests.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     */
    public void handleLaunchRequest(LtiLaunchRequestDTO launchRequest, Exercise exercise) {

        // Authenticate the the LTI user
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
                log.debug("Remembering launchRequest for session ID {}", authDetails.getSessionId());
                sessionId = authDetails.getSessionId();
            }

            // Found it. Remember the launch request for later login.
            if (sessionId != null) {
                log.debug("Remembering launchRequest for session ID {}", sessionId);
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
        User user = userService.getUserWithGroupsAndAuthorities();

        // Make sure user is added to group for this exercise
        addUserToExerciseGroup(user, exercise.getCourse());

        // Save LTI user ID to automatically sign in the next time
        saveLtiUserId(user, launchRequest.getUser_id());

        // Save LTI outcome url
        saveLtiOutcomeUrl(user, exercise, launchRequest.getLis_outcome_service_url(), launchRequest.getLis_result_sourcedid());
    }

    /**
     * Signs in the LTI user into the exercise app. Therefore it creates an user, if necessary.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @return
     * @throws ArtemisAuthenticationException
     * @throws AuthenticationException
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

        final var email = launchRequest.getLis_person_contact_email_primary() != null ? launchRequest.getLis_person_contact_email_primary()
                : launchRequest.getUser_id() + "@lti.artemis.ase.in.tum.de";
        final var username = createUsernameFromLaunchRequest(launchRequest);
        final var fullname = launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id();

        // 2. Case: Existing mapping for LTI user id
        // Check if there is an existing mapping for the user ID
        final var optionalLtiUserId = ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id());
        if (optionalLtiUserId.isPresent()) {
            final var user = optionalLtiUserId.get().getUser();
            // Authenticate
            return Optional.of(
                    new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))));
        }

        // 3. Case: Lookup user with the LTI email address. Sign in as this user.
        // Check if lookup by email is enabled
        if (launchRequest.getCustom_lookup_user_by_email()) {
            // check if an user with this email address exists
            final var usernameLookupByEmail = artemisAuthenticationProvider.getUsernameForEmail(email);
            if (usernameLookupByEmail.isPresent()) {
                return loginUserByEmail(usernameLookupByEmail.get(), email, fullname);
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
            if (TUMX.equals(launchRequest.getContext_label())) {
                groups.add(USER_GROUP_NAME_EDX);
                newUser = userService.createUser(username, groups, USER_GROUP_NAME_EDX, fullname, email, null, "en");
            }
            else if (U4I.equals(launchRequest.getContext_label())) {
                groups.add(USER_GROUP_NAME_U4I);
                newUser = userService.createUser(username, groups, USER_GROUP_NAME_U4I, fullname, email, null, "en");
            }
            else {
                throw new InternalAuthenticationServiceException("Unknown context_label sent in LTI Launch Request: " + launchRequest.toString());
            }

            return newUser;
        });

        // Make sure the user is activated
        if (!user.getActivated()) {
            userService.activateRegistration(user.getActivationKey());
        }

        log.debug("Signing in as {}", username);
        return Optional
                .of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))));
    }

    private Optional<Authentication> loginUserByEmail(String username, String email, String fullname) {
        log.info("Signing in as {}", username);
        final var user = artemisAuthenticationProvider.getOrCreateUser(new UsernamePasswordAuthenticationToken(username, ""), USER_GROUP_NAME_EDX, fullname, email, true);

        return Optional
                .of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Collections.singletonList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))));
    }

    @NotNull
    private String createUsernameFromLaunchRequest(LtiLaunchRequestDTO launchRequest) {
        final String username;
        if (TUMX.equals(launchRequest.getContext_label())) {
            username = this.USER_PREFIX_EDX + (launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id());
        }
        else if (U4I.equals(launchRequest.getContext_label())) {
            username = this.USER_PREFIX_U4I + (launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id());
        }
        else {
            throw new InternalAuthenticationServiceException("Unknown context_label sent in LTI Launch Request: " + launchRequest.toString());
        }

        return username;
    }

    /**
     * Add an user to the course student group
     *
     * @param user
     * @param course
     */
    private void addUserToExerciseGroup(User user, Course course) {
        String courseStudentGroupName = course.getStudentGroupName();
        if (!user.getGroups().contains(courseStudentGroupName)) {
            Set<String> groups = user.getGroups();
            groups.add(courseStudentGroupName);
            user.setGroups(groups);
            userRepository.save(user);

            if (!user.getLogin().startsWith("edx")) {
                // try to sync with authentication service for actual users (not for edx users)
                try {
                    artemisAuthenticationProvider.addUserToGroup(user.getLogin(), courseStudentGroupName);
                }
                catch (ArtemisAuthenticationException e) {
                    /*
                     * This might throw exceptions, for example if the group does not exist on the authentication service. We can safely ignore them.
                     */
                }
            }
        }
    }

    /**
     * Save the LTO outcome url
     *
     * @param user
     * @param exercise
     * @param url
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
     * @param user
     * @param ltiUserIdString
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
     * @return True if the request is valid, otherwise false
     */
    public Boolean verifyRequest(HttpServletRequest request) {
        LtiVerifier ltiVerifier = new LtiOauthVerifier();
        Boolean success = false;
        try {
            LtiVerificationResult ltiResult = ltiVerifier.verify(request, this.OAUTH_SECRET);
            success = ltiResult.getSuccess();
            if (!success) {
                log.error("Lti signature verification failed: " + ltiResult.getMessage());
            }
        }
        catch (LtiVerificationException e) {
            log.error("Lti signature verification failed. ", e);
        }
        return success;

    }

    /**
     * This method is pinged on new build results. It sends an message to the LTI consumer with the new score.
     *
     * @param participation The programming exercise participation for which a new build result is available
     */
    public void onNewBuildResult(ProgrammingExerciseStudentParticipation participation) {

        // Get the LTI outcome URL
        ltiOutcomeUrlRepository.findByUserAndExercise(participation.getStudent(), participation.getExercise()).ifPresent(ltiOutcomeUrl -> {

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
                HttpPost request = IMSPOXRequest.buildReplaceResult(ltiOutcomeUrl.getUrl(), OAUTH_KEY, OAUTH_SECRET, ltiOutcomeUrl.getSourcedId(), score, null, false);
                HttpClient client = HttpClientBuilder.create().build();
                HttpResponse response = client.execute(request);
                String responseString = new BasicResponseHandler().handleResponse(response);
                log.info("Response from LTI consumer: {}", responseString);
                if (response.getStatusLine().getStatusCode() >= 400) {
                    throw new HttpResponseException(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                }
            }
            catch (Exception e) {
                log.error("Reporting to LTI consumer failed: {}", e, e);
            }
        });
    }

    /**
     * Handle launch request which was initiated earlier by a LTI consumer
     *
     * @param sessionId The ID of the current user's session (JSESSIONID)
     */
    public void handleLaunchRequestForSession(String sessionId) {
        if (launchRequestForSession.containsKey(sessionId)) {

            log.debug("Found LTI launchRequest for session ID {}", sessionId);

            LtiLaunchRequestDTO launchRequest = launchRequestForSession.get(sessionId).getLeft();
            Exercise exercise = launchRequestForSession.get(sessionId).getRight();

            onSuccessfulLtiAuthentication(launchRequest, exercise);

            // clean up
            launchRequestForSession.remove(sessionId);

        }
    }

}
