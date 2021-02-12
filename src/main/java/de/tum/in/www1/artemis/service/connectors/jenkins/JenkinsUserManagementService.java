package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.JenkinsUpdateUserDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.JenkinsUserDTO;

@Service
@Profile("jenkins")
public class JenkinsUserManagementService implements CIUserManagementService {

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    private final RestTemplate restTemplate;

    private final JenkinsJobService jenkinsJobService;

    private UserService userService;

    public JenkinsUserManagementService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsJobService jenkinsJobService) {
        this.restTemplate = restTemplate;
        this.jenkinsJobService = jenkinsJobService;
    }

    @Autowired // break the cycle
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Creates a user in Jenkins. Note that the user login acts as
     * a unique identifier in Jenkins.
     *
     * @param user The user to create
     */
    @Override
    public void createUser(User user) {

        // Only create a user if it doesn't already exist.
        if (getUser(user.getLogin()).isPresent()) {
            return;
        }

        // Make sure the user login contains legal characters.
        if (!isUserLoginLegal(user)) {
            return;
        }

        var password = userService.decryptPassword(user);
        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("username", user.getLogin());
        formData.add("password1", password);
        formData.add("password2", password);
        formData.add("fullname", user.getName());
        formData.add("email", user.getEmail());

        var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("securityRealm", "createAccountByAdmin").build().toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.POST, createApplicationFormHttpEntity(formData), void.class);
        }
        catch (RestClientException e) {
            // TODO: Log errors and handle accordingly
            e.printStackTrace();
        }
    }

    @Override
    public void deleteUser(String userLogin) {
        // Only delete a user if it exists.
        if (getUser(userLogin).isEmpty()) {
            return;
        }

        var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("user", userLogin, "doDelete").build().toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.POST, null, void.class);
        }
        catch (RestClientException e) {
            // TODO: Log errors and handle accordingly
            e.printStackTrace();
        }
    }

    /**
     * Updates the user in Jenkins with the user data from Artemis. Note that it's not possible
     * to change the username of the Jenkins user.
     *
     * @param user The user to update.
     */
    @Override
    public void updateUser(User user) {
        // Only update a user if it exists.
        if (getUser(user.getLogin()).isEmpty()) {
            return;
        }

        var password = userService.decryptPassword(user);
        var formData = new LinkedMultiValueMap<String, String>();
        formData.add("user.password", password);
        formData.add("user.password2", password);
        formData.add("_.fullName", user.getName());
        formData.add("email.address", user.getEmail());
        formData.add("_.description", "");
        formData.add("_.primaryViewName", "");
        formData.add("providerId", "default");
        formData.add("_.authorizedKeys", "");
        formData.add("insensitiveSearch", "on");
        formData.add("_.timeZoneName", "");
        formData.add("core:apply", "true");

        try {
            formData.add("json", getUpdateUserJson(user));
        }
        catch (Exception e) {
            // TODO: Handle accordingly
            e.printStackTrace();
            return;
        }

        var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("user", user.getLogin(), "configSubmit").build().toUri();
        try {
            restTemplate.exchange(uri, HttpMethod.POST, createApplicationFormHttpEntity(formData), String.class);

            // jenkinsJobService.addPermissionsForUserToJob(user.getLogin(), "TESTCOURSETESTFRANCISCO", List.of(JenkinsJobPermission.JOB_READ, JenkinsJobPermission.JOB_CONFIGURE));
            jenkinsJobService.removePermissionsFromUserOfJob(user.getLogin(), "TESTCOURSETESTFRANCISCO",
                    List.of(JenkinsJobPermission.JOB_READ, JenkinsJobPermission.JOB_CONFIGURE));
        }
        catch (RestClientException | IOException e) {
            // TODO: Log errors and handle accordingly
            e.printStackTrace();
        }
    }

    /**
     * Returns json containing information about the user to update in Jenkins.
     * This is required in addition to the form data.
     *
     * @param user The user to update
     * @return Json for Jenkins
     * @throws JsonProcessingException when something goes wrong writing the json content.
     */
    public String getUpdateUserJson(User user) throws JsonProcessingException {
        var updateUserDto = new JenkinsUpdateUserDTO();
        updateUserDto.setFullName(user.getName());
        updateUserDto.setDescription("");
        updateUserDto.setAddress(user.getEmail());
        updateUserDto.setPrimaryViewName("");
        updateUserDto.setProviderId("default");
        updateUserDto.setPassword(user.getPassword());
        updateUserDto.setAuthorizedKeys("");
        updateUserDto.setInsensitiveSearch(true);
        updateUserDto.setTimeZoneName("");
        return new ObjectMapper().writeValueAsString(updateUserDto);
    }

    private HttpEntity<MultiValueMap<String, String>> createApplicationFormHttpEntity(MultiValueMap<String, String> formData) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return new HttpEntity<>(formData, headers);
    }

    /**
     * Gets a Jenkins user or returns empty if the user wasn't found.
     *
     * @param userLogin the username of the user to look up
     * @return the user or empty if the user doesn't exist
     */
    public Optional<JenkinsUserDTO> getUser(String userLogin) {
        var uri = UriComponentsBuilder.fromHttpUrl(jenkinsServerUrl.toString()).pathSegment("user", userLogin, "api", "json").build().toUri();
        try {
            final var response = restTemplate.exchange(uri, HttpMethod.GET, null, JenkinsUserDTO.class);
            return Optional.ofNullable(response.getBody());
        }
        catch (RestClientException e) {
            // TODO: Log errors if status is not 404
            return Optional.empty();
        }
    }

    /**
     * The Jenkins username acts as a unique identifier and
     * can only contain alphanumeric characters, underscore and dash
     *
     * @param user The user
     * @return whether the user login is legal or not
     */
    private boolean isUserLoginLegal(User user) {
        String regex = "^[a-zA-Z0-9_-]*$";
        return user.getLogin().matches(regex);
    }
}
