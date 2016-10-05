package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.util.PatchedIMSPOXRequest;
import de.tum.in.www1.exerciseapp.exception.JiraException;
import de.tum.in.www1.exerciseapp.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import de.tum.in.www1.exerciseapp.security.SecurityUtils;
import de.tum.in.www1.exerciseapp.web.rest.dto.LtiLaunchRequestDTO;
import org.apache.commons.lang.RandomStringUtils;
import org.imsglobal.lti.launch.LtiOauthVerifier;
import org.imsglobal.lti.launch.LtiVerificationException;
import org.imsglobal.lti.launch.LtiVerificationResult;
import org.imsglobal.lti.launch.LtiVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private RemoteUserService remoteUserService;


    @Inject
    private UserService userService;

    @Inject
    private UserRepository userRepository;

    @Inject
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Inject
    private ResultRepository resultRepository;

    @Autowired
    AuthenticationSuccessHandler successHandler;

    /**
     * Handles LTI launch requests.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     * @throws JiraException
     * @throws AuthenticationException
     */
    public void handleLaunchRequest(LtiLaunchRequestDTO launchRequest, Exercise exercise) throws JiraException, AuthenticationException {


        User user = authenticateLtiUser(launchRequest);

        // Make sure user is added to group for this exercise
        addUserToExerciseGroup(user, exercise);

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
    private User authenticateLtiUser(LtiLaunchRequestDTO launchRequest) throws JiraException, AuthenticationException {
        String username = this.USER_PREFIX + (launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id());


        String password;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof AnonymousAuthenticationToken) {
            // currently not logged in

            // check if user already exists
            Optional<String> existingPassword = userService.decryptPasswordByLogin(username);
            if (existingPassword.isPresent()) {

                password = existingPassword.get();

                log.debug("User {} already exists, signing in", username);

            } else {
                // user needs to be created

                password = RandomStringUtils.randomAlphanumeric(10);
                String email = launchRequest.getLis_person_contact_email_primary() != null ? launchRequest.getLis_person_contact_email_primary() : launchRequest.getUser_id() + "@lti.exercisebruegge.in.tum.de";
                String fullname = launchRequest.getLis_person_sourcedid() != null ? launchRequest.getLis_person_sourcedid() : launchRequest.getUser_id();

                remoteUserService.createUser(username,
                    password,
                    email,
                    fullname);

                remoteUserService.addUserToGroup(username, USER_GROUP_NAME);

                log.debug("Created user {} {} on JIRA, signing in", username, password);

            }

            // Authenticate, which will create the local user
            Authentication token = remoteUserService.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(token);

            // Save password if the user was newly created
            if (!existingPassword.isPresent()) {
                userService.changePasswordByLogin(username, password);
            }


        } else {
            log.debug("User already signed in");
            username = SecurityUtils.getCurrentUserLogin();
        }

        return userRepository.findOneByLogin(username).get();


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
            remoteUserService.addUserToGroup(user.getLogin(), courseGroup);
            List<String> groups = user.getGroups();
            groups.add(courseGroup);
            user.setGroups(groups);
            userRepository.save(user);
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

        if(url == null || url.isEmpty()) {
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

        if(ltiOutcomeUrl.isPresent()) {

            String score = getScoreForParticipation(participation);

            log.debug("Reporting to LTI consumer: Score {} for Participation {}", score, participation);


            try {
                PatchedIMSPOXRequest.sendReplaceResult(ltiOutcomeUrl.get().getUrl(), OAUTH_KEY, OAUTH_SECRET, ltiOutcomeUrl.get().getSourcedId(), score);
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
        if(!latestResult.isPresent()) {
            return "0.00";
        }

        if(latestResult.get().isBuildSuccessful()) {
            return "1.00";
        }

        if(latestResult.get().getResultString() != null && !latestResult.get().getResultString().isEmpty()) {

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


}
