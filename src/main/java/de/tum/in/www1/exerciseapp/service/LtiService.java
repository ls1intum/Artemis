package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.config.JHipsterProperties;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.util.PatchedIMSPOXRequest;
import de.tum.in.www1.exerciseapp.exception.JiraException;
import de.tum.in.www1.exerciseapp.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.exerciseapp.repository.LtiUserIdRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.security.JiraAuthenticationProvider;
import de.tum.in.www1.exerciseapp.security.SecurityUtils;
import de.tum.in.www1.exerciseapp.service.util.RandomUtil;
import de.tum.in.www1.exerciseapp.web.rest.dto.LtiLaunchRequestDTO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
@Transactional
public class LtiService {

    private final Logger log = LoggerFactory.getLogger(LtiService.class);


    @Value("${exerciseapp.lti.oauth-key}")
    private String OAUTH_KEY;

    @Value("${exerciseapp.lti.oauth-secret}")
    private String OAUTH_SECRET;

    @Value("${exerciseapp.lti.user-prefix}")
    private String USER_PREFIX = "";

    @Value("${exerciseapp.lti.user-group-name}")
    private String USER_GROUP_NAME = "lti";


    @Inject
    private UserService userService;

    @Inject
    private UserRepository userRepository;

    @Inject
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Inject
    private ResultRepository resultRepository;

    @Inject
    private PasswordEncoder passwordEncoder;

    @Inject
    private JiraAuthenticationProvider jiraAuthenticationProvider;

    @Inject
    private LtiUserIdRepository ltiUserIdRepository;


    @Inject
    private HttpServletResponse response;

    public HashMap<String, Pair<LtiLaunchRequestDTO, Exercise>> launchRequestForSession = new HashMap<>();



    /**
     * Handles LTI launch requests.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     * @throws JiraException
     * @throws AuthenticationException
     */
    public void handleLaunchRequest(LtiLaunchRequestDTO launchRequest, Exercise exercise) throws JiraException, AuthenticationException {


        Optional<Authentication> auth = authenticateLtiUser(launchRequest);

        if (auth.isPresent()) {

            SecurityContextHolder.getContext().setAuthentication(auth.get());

            onSuccessfulLtiAuthentication(launchRequest, exercise);

        } else  {

            /*
             *
             * None of the  auth methods were successful.
             * -> Map the launchRequest to the Session ID
             * -> If the user signs in manually later, we use it in LtiAuthenticationSuccessListener
             *
             */


            // Find (new) session ID
            String sessionId = null;
            if(response.containsHeader("Set-Cookie")) {
                for(String cookie: response.getHeaders("Set-Cookie")){
                    if(cookie.contains("JSESSIONID")) {
                        Pattern pattern = Pattern.compile("=(.*?);");
                        Matcher matcher = pattern.matcher(response.getHeader("Set-Cookie"));
                        if (matcher.find()) {
                            sessionId = matcher.group(1);
                        }
                        break;
                    }
                }
            }
            if(sessionId == null) {
                WebAuthenticationDetails authDetails = (WebAuthenticationDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();
                log.debug("Remembering launchRequest for session ID {}", authDetails.getSessionId());
                sessionId = authDetails.getSessionId();
            }


            // Found it. Save launch request.
            if(sessionId != null) {
                log.debug("Remembering launchRequest for session ID {}", sessionId);
                launchRequestForSession.put(sessionId, Pair.of(launchRequest, exercise));
            }

        }

    }


    /**
     * Handler for successful LTI auth
     * Saves the LTI outcome url and permanently maps the LTI user id to the user
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     */
    public void onSuccessfulLtiAuthentication(LtiLaunchRequestDTO launchRequest, Exercise exercise) {
        // Auth was successful
        User user = userService.getUser();

        // Make sure user is added to group for this exercise
        addUserToExerciseGroup(user, exercise);

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
     * @throws JiraException
     * @throws AuthenticationException
     */
    private Optional<Authentication> authenticateLtiUser(LtiLaunchRequestDTO launchRequest) throws JiraException, AuthenticationException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();


        if (SecurityUtils.isAuthenticated()) {
            /**
             * 1. Case:
             * User is already signed in. We are done here.
             *
             */
            return Optional.of(auth);
        }



        if(launchRequest.getUser_id().equals("student")) {
            throw new InternalAuthenticationServiceException("Invalid username sent by launch request. Please do not launch the exercise from edX studio. Use 'Preview' instead.");
        }



        String email = launchRequest.getLis_person_contact_email_primary() != null ? launchRequest.getLis_person_contact_email_primary() : launchRequest.getUser_id() + "@lti.exercisebruegge.in.tum.de";
        String username = this.USER_PREFIX + (launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id());
        String fullname = launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id();


        Optional<LtiUserId> ltiUserId = ltiUserIdRepository.findByLtiUserId(launchRequest.getUser_id());

        if(ltiUserId.isPresent()) {
            /*
             * 2. Case:
             * Existing mapping for LTI user id
             *
             */
            User user = ltiUserId.get().getUser();
            return Optional.of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Arrays.asList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))));
        }


        if (launchRequest.getCustom_lookup_user_by_email() == true) {

            /*
             * 3. Case:
             * Lookup JIRA user with the LTI email address.
             * Sign in as this user.
             *
             */

            // check if an JIRA user with this email address exists
            Optional<String> jiraLookupByEmail = jiraAuthenticationProvider.getUsernameForEmail(email);


            if (jiraLookupByEmail.isPresent()) {
                log.debug("Signing in as {}", jiraLookupByEmail.get());
                User user = jiraAuthenticationProvider.getOrCreateUser(new UsernamePasswordAuthenticationToken(jiraLookupByEmail.get(), ""), true);

                return Optional.of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Arrays.asList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))));
            }
        }
        if (launchRequest.getCustom_require_existing_user() == false) {
            /*
             * 4. Case:
             * Create new user
             *
             */


            User user = userRepository.findOneByLogin(username).orElseGet(() -> {
                User newUser = userService.createUserInformation(username, "",
                    USER_GROUP_NAME, fullname, email,
                    "en");

                // add user to LTI group
                newUser.setGroups(new ArrayList<>(Arrays.asList(USER_GROUP_NAME)));

                // set random password
                String randomEncryptedPassword = passwordEncoder.encode(RandomUtil.generatePassword());
                newUser.setPassword(randomEncryptedPassword);

                userRepository.save(newUser);

                log.debug("Created user {}", username);

                return newUser;
            });


            if (!user.getActivated()) {
                userService.activateRegistration(user.getActivationKey());
            }

            log.debug("Signing in as {}", username);
            return Optional.of(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), Arrays.asList(new SimpleGrantedAuthority(AuthoritiesConstants.USER))));

        }


        return Optional.empty();

    }

    /**
     * Add an user to the exercise group
     *
     * @param user
     * @param exercise
     */
    private void addUserToExerciseGroup(User user, Exercise exercise) {
        String courseGroup = exercise.getCourse().getStudentGroupName();
        if (!user.getGroups().contains(courseGroup)) {
            List<String> groups = user.getGroups();
            groups.add(courseGroup);
            user.setGroups(groups);
            userRepository.save(user);

            // try to sync with JIRA
            try {
                jiraAuthenticationProvider.addUserToGroup(user.getLogin(), courseGroup);
            } catch (JiraException e) {
            /*
                This might throw exceptions, for example if the group does not exist on JIRA.
                We can safely ignore them.
            */
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
        } catch (LtiVerificationException e) {
            log.error("Lti signature verification failed. ", e);
        }
        return success;

    }


    /**
     * This method is pinged on new build results.
     * It sends an message to the LTI consumer with the new score.
     *
     * @param participation
     */
    public void onNewBuildResult(Participation participation) {

        Optional<LtiOutcomeUrl> ltiOutcomeUrl = ltiOutcomeUrlRepository.findByUserAndExercise(participation.getStudent(), participation.getExercise());

        if (ltiOutcomeUrl.isPresent()) {

            String score = getScoreForParticipation(participation);

            log.debug("Reporting to LTI consumer: Score {} for Participation {}", score, participation);


            try {
                HttpPost request = PatchedIMSPOXRequest.buildReplaceResult(ltiOutcomeUrl.get().getUrl(), OAUTH_KEY, OAUTH_SECRET, ltiOutcomeUrl.get().getSourcedId(), score, null, false);
                HttpClient client = HttpClientBuilder.create().build();
                HttpResponse response = client.execute(request);
                String responseString = new BasicResponseHandler().handleResponse(response);
                log.info("Response from LTI consumer: {}", responseString);
                if (response.getStatusLine().getStatusCode() >= 400) {
                    throw new HttpResponseException(response.getStatusLine().getStatusCode(),
                        response.getStatusLine().getReasonPhrase());
                }
            } catch (Exception e) {
                log.error("Reporting to LTI consumer failed: {}", e);
            }


        }

    }

    /**
     * Calculates the score for a participation. Therefore is uses the number of successful tests in the latest build.
     *
     * @param participation
     * @return score String value between 0.00 and 1.00
     */
    public String getScoreForParticipation(Participation participation) {

        Optional<Result> latestResult = resultRepository.findFirstByParticipationIdOrderByBuildCompletionDateDesc(participation.getId());
        if (!latestResult.isPresent()) {
            return "0.00";
        }

        if (latestResult.get().isBuildSuccessful()) {
            return "1.00";
        }

        if (latestResult.get().getResultString() != null && !latestResult.get().getResultString().isEmpty()) {

            Pattern p = Pattern.compile("^([0-9]+) of ([0-9]+) failed");
            Matcher m = p.matcher(latestResult.get().getResultString());

            if (m.find()) {
                float failedTests = Float.parseFloat(m.group(1));
                float totalTests = Float.parseFloat(m.group(2));
                float score = (totalTests - failedTests) / totalTests;
                return String.format(Locale.ROOT, "%.2f", score);
            }

        }


        return "0.00";

    }


    /**
     * Handle launch request which was initiated earlier by a LTI consumer
     *
     * @param sessionId
     */
    public void handleLaunchRequestForSession(String sessionId) {
        if(launchRequestForSession.containsKey(sessionId)) {

            log.debug("Found LTI launchRequest for session ID {}", sessionId);

            LtiLaunchRequestDTO launchRequest = launchRequestForSession.get(sessionId).getLeft();
            Exercise exercise = launchRequestForSession.get(sessionId).getRight();

            onSuccessfulLtiAuthentication(launchRequest, exercise);

            // clean up
            launchRequestForSession.remove(sessionId);

        }
    }


}
