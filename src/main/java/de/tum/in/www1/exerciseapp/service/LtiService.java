package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.config.SecurityConfiguration;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.exception.JiraException;
import de.tum.in.www1.exerciseapp.security.JiraAuthenticationProvider;
import de.tum.in.www1.exerciseapp.web.rest.dto.LtiLaunchRequestDTO;
import org.imsglobal.lti.launch.LtiOauthVerifier;
import org.imsglobal.lti.launch.LtiVerificationException;
import org.imsglobal.lti.launch.LtiVerificationResult;
import org.imsglobal.lti.launch.LtiVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;


@Service
@Transactional
public class LtiService {

    private final Logger log = LoggerFactory.getLogger(LtiService.class);


    @Value("${exerciseapp.lti.oauth-key}")
    private String OAUTH_KEY;

    @Value("${exerciseapp.lti.oauth-secret}")
    private String OAUTH_SECRET;

    @Value("${exerciseapp.lti.create-user-prefix}")
    private String CREATE_USER_PREFIX="";

    @Inject
    JiraService jiraService;


    @Inject
    private JiraAuthenticationProvider jiraAuthenticationProvider;

    @Autowired
    AuthenticationSuccessHandler successHandler;

    /**
     * Handles LTI launch requests. Therefore it creates an user, and signs in if necessary.
     *
     * @param launchRequest The launch request, sent by LTI consumer
     * @param exercise Exercise to launch
     * @throws JiraException
     * @throws AuthenticationException
     */
    public void handleLaunchRequest(LtiLaunchRequestDTO launchRequest, Exercise exercise) throws  JiraException, AuthenticationException {


        // TODO: handle if user already logged in


        String username = this.CREATE_USER_PREFIX + launchRequest.getLis_person_sourcedid();
        String password = "test123";

        jiraService.createUser(username,
            password,
            launchRequest.getLis_person_contact_email_primary(),
            launchRequest.getLis_person_sourcedid());

        // Add user to the group for this exercise
        jiraService.addUserToGroup(username, exercise.getCourse().getStudentGroupName());


        Authentication token = jiraAuthenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        SecurityContextHolder.getContext().setAuthentication(token);



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
            if(!success) {
                log.error("Lti signature verification failed: " + ltiResult.getMessage());
            }
        } catch (LtiVerificationException e) {
            log.error("Lti signature verification failed. ", e);
        }
        return success;

    }


}
