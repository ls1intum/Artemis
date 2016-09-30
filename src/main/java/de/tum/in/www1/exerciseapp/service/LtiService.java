package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.exception.JiraException;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
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
import java.util.Optional;


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


    @Autowired
    AuthenticationSuccessHandler successHandler;

    /**
     * Handles LTI launch requests. Therefore it creates an user, and signs in if necessary.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise      Exercise to launch
     * @throws JiraException
     * @throws AuthenticationException
     */
    public void handleLaunchRequest(LtiLaunchRequestDTO launchRequest, Exercise exercise) throws JiraException, AuthenticationException {

        String username = (this.USER_PREFIX + launchRequest.getLis_person_sourcedid());
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

                remoteUserService.createUser(username,
                    password,
                    launchRequest.getLis_person_contact_email_primary(),
                    launchRequest.getLis_person_sourcedid());

                remoteUserService.addUserToGroup(username, USER_GROUP_NAME);

                log.debug("Created user {} on JIRA, signing in", username);

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
        }


        // Make sure user is added to group for this exercise
        User user  =userRepository.findOneByLogin(username).get();

        if(user.getLogin().equals(username)) {
            String courseGroup = exercise.getCourse().getStudentGroupName();

            if(!user.getGroups().contains(courseGroup)) {
                remoteUserService.addUserToGroup(username, courseGroup);
                List<String> groups = user.getGroups();
                groups.add(courseGroup);
                user.setGroups(groups);
                userRepository.save(user);
            }
        }


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


}
