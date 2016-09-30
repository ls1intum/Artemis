package de.tum.in.www1.exerciseapp.service;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;


import de.tum.in.www1.exerciseapp.exception.JiraException;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.inject.Inject;

/**
 * Created by Josias Montag on 22.09.16.
 */
@Service
@Transactional
public class JiraService {
    private final Logger log = LoggerFactory.getLogger(JiraService.class);


    @Value("${exerciseapp.jira.instructor-group-name}")
    private String INSTRUCTOR_GROUP_NAME;

    @Value("${exerciseapp.jira.url}")
    private URL JIRA_URL;

    @Value("${exerciseapp.bitbucket.url}")
    private URL BITBUCKET_URL;

    @Value("${exerciseapp.jira.user}")
    private String JIRA_USER;

    @Value("${exerciseapp.jira.password}")
    private String JIRA_PASSWORD;


    /**
     * Creates a JIRA user with given password.
     *
     * @param username     The wanted JIRA username
     * @param emailAddress The eMail address for the user
     * @param displayName  The display name (full name)
     * @throws JiraException if JIRA returns an error
     */

    public void createUser(String username, String password, String emailAddress, String displayName) throws JiraException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        body.put("emailAddress", emailAddress);
        body.put("password", password);
        body.put("displayName", displayName);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                JIRA_URL + "/rest/api/2/user",
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            log.error("Could not create JIRA user " + username, e);
            throw new JiraException("Error while creating user");
        }

        /**
         * As default JIRA <-> Bitbucket user directory is synced every hour.
         * Unfortunately there is no way to sync the user database via API. See also: https://jira.atlassian.com/browse/BSERV-5108
         *
         * Workaround: "login" to Bitbucket with the new user via any API request. By this the new user is synced to Bitbucket.
         *
         */

        headers = HeaderUtil.createAuthorization(username, password);
        entity = new HttpEntity<>(headers);
        restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                BITBUCKET_URL + "/rest/api/1.0/repos",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            // ignore
        }


    }

    /**
     * Adds a JIRA user to a JIRA group. Ignores "user is already a member of" errors.
     *
     * @param username The JIRA username
     * @param group    The JIRA group name
     * @throws JiraException if JIRA returns an error
     */
    public void addUserToGroup(String username, String group) throws JiraException {
        HttpHeaders headers = HeaderUtil.createAuthorization(JIRA_USER, JIRA_PASSWORD);
        Map<String, Object> body = new HashMap<>();
        body.put("name", username);
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                JIRA_URL + "/rest/api/2/group/user?groupname=" + group,
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().equals(HttpStatus.BAD_REQUEST)
                && e.getResponseBodyAsString().contains("user is already a member of")) {
                // ignore the error if the user is already in the group
                return;
            }
            log.error("Could not add JIRA user to group " + group, e);
            throw new JiraException("Error while adding user to group");
        }
    }


}
