package de.tum.in.www1.artemis.service.connectors.bitbucket;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooInternalUrlService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooRepositoryDTO;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationUpdateService;

@Service
@Profile("bamboo")
public class BambooBuildPlanUpdateService implements ContinuousIntegrationUpdateService {

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    private final BambooInternalUrlService bambooInternalUrlService;

    private static final String OLD_ASSIGNMENT_REPO_NAME = "Assignment";

    private final Logger log = LoggerFactory.getLogger(BambooBuildPlanUpdateService.class);

    private final RestTemplate bambooRestTemplate;

    public BambooBuildPlanUpdateService(@Qualifier("bambooRestTemplate") RestTemplate bambooRestTemplate, BambooInternalUrlService bambooInternalUrlService) {
        this.bambooRestTemplate = bambooRestTemplate;
        this.bambooInternalUrlService = bambooInternalUrlService;
    }

    @Override
    public void updatePlanRepository(String buildPlanKey, String bambooRepositoryName, String newRepoUrl, String branchName) {
        try {
            log.debug("Update plan repository for build plan {}", buildPlanKey);
            BambooRepositoryDTO bambooRepository = findBambooRepository(bambooRepositoryName, OLD_ASSIGNMENT_REPO_NAME, buildPlanKey);
            if (bambooRepository == null) {
                throw new BambooException("Something went wrong while updating the template repository of the build plan " + buildPlanKey
                        + " to the student repository : Could not find assignment nor Assignment repository");
            }

            updateBambooPlanRepository(bambooRepository, buildPlanKey, branchName, bambooInternalUrlService.toInternalVcsUrl(newRepoUrl));

            log.info("Update plan repository for build plan {} was successful", buildPlanKey);
        }
        catch (Exception e) {
            throw new BambooException(
                    "Something went wrong while updating the template repository of the build plan " + buildPlanKey + " to the student repository : " + e.getMessage(), e);
        }
    }

    /**
     * Update the build plan repository using the cli plugin. This is e.g. invoked, when a student starts a programming exercise.
     * Then the build plan (which was cloned before) needs to be updated to work with the student repository
     *
     * @param bambooRepository the bamboo repository which was obtained before
     * @param buildPlanKey     the complete name of the plan
     */
    private void updateBambooPlanRepository(@NotNull BambooRepositoryDTO bambooRepository, String buildPlanKey, String branchName, String newRepoUrl) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("planKey", buildPlanKey);
        parameters.add("selectedRepository", "com.atlassian.bamboo.plugins.atlassian-bamboo-plugin-git:gitv2");
        // IMPORTANT: Don't change the name of the repo! We depend on the naming (assignment, tests) in some other parts of the application
        parameters.add("repositoryName", bambooRepository.getName());
        parameters.add("repositoryId", Long.toString(bambooRepository.getId()));
        parameters.add("confirm", "true");
        parameters.add("save", "Save repository");
        parameters.add("bamboo.successReturnMode", "json");
        parameters.add("repository.git.branch", branchName);
        parameters.add("repository.git.repositoryUrl", newRepoUrl);
        parameters.add("repository.git.authenticationType", "PASSWORD");
        parameters.add("repository.git.passwordCredentialsSource", "SHARED_CREDENTIALS");
        parameters.add("repository.git.useShallowClones", "true");
        parameters.add("repository.git.commandTimeout", "180");

        try {
            String requestUrl = bambooServerUrl + "/chain/admin/config/updateRepository.action";
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
            var response = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, Void.class);
            log.info("Response from Bamboo updateRepository.action {}", response);
        }
        catch (Exception ex) {
            // TODO: improve error handling
            String message = "Request failed on the server with response code 500. Make sure all required fields have been provided using the various field and value parameters. "
                    + "The server log may provide insight into missing fields: " + ex.getMessage();
            throw new BambooException(message);
        }
    }

    /**
     * Fetches the list of repositories in the given build plan, then tries to find the repository in the bamboo build plan, first using the firstRepositoryName,
     * second using the secondRepositoryName
     *
     * @param firstRepositoryName  the repository name that should be found first
     * @param secondRepositoryName in case firstRepositoryName is not found, the repository name that should be found second
     * @param buildPlanKey         the bamboo build plan key in which the repository with the specified name should be found
     * @return the found bamboo repository in the build plan or null in case the repository with the specified name could not be found
     */
    private BambooRepositoryDTO findBambooRepository(String firstRepositoryName, @Nullable String secondRepositoryName, String buildPlanKey) {
        List<BambooRepositoryDTO> list = this.getBuildPlanRepositoryList(buildPlanKey);
        var bambooRepository = this.lookupRepository(firstRepositoryName, list);
        if (bambooRepository == null && secondRepositoryName != null) {
            bambooRepository = this.lookupRepository(secondRepositoryName, list);
        }
        return bambooRepository;
    }

    private BambooRepositoryDTO lookupRepository(String name, List<BambooRepositoryDTO> list) {
        var repository = list.stream().filter(repo -> name.equals(repo.getName())).findFirst();
        if (repository.isPresent()) {
            return repository.get();
        }

        if (StringUtils.isNumeric(name)) {
            Long repoId = Long.valueOf(name);
            repository = list.stream().filter(repo -> repoId.equals(repo.getId())).findFirst();
            if (repository.isPresent()) {
                return repository.get();
            }
        }

        return null;
    }

    private List<BambooRepositoryDTO> getBuildPlanRepositoryList(String buildPlanKey) {
        List<BambooRepositoryDTO> list = new ArrayList<>();
        String requestUrl = bambooServerUrl + "/chain/admin/config/editChainRepository.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("buildKey", buildPlanKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.TEXT_HTML));
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        String data = bambooRestTemplate.exchange(builder.build().toUri(), HttpMethod.GET, entity, String.class).getBody();

        // the following code finds the required information in the html response
        // it iterates over all data-item-ids in the html code and tries to find the required repository information inside
        // please note: while this approach is not ideal, it works reliably, and there is currently no other API to get the repository id which is needed for other calls to Bamboo
        if (data != null) {
            // Pattern matching the repo id saved in an HTML attribute and the name which is saved in a HTML tag
            // Example: data-item-id="123" ... "item-title">Assignment</span> with the id 123 and the name Assignment
            Pattern findPattern = Pattern.compile("data-item-id=\"(\\d+)\".*\"item-title\">([^<]+)<", Pattern.DOTALL);
            int endIndex = 0;
            int count = 0;
            // use startIndex and endIndex to navigate through the document, try at most 100 times
            while (count < 100) {
                int startIndex = data.indexOf("data-item-id", endIndex);
                endIndex = data.indexOf("</li>", startIndex);
                if (startIndex < 0 || endIndex < 0) {
                    // not found
                    break;
                }
                Matcher matcher = findPattern.matcher(data.substring(startIndex, endIndex));
                if (matcher.find()) {
                    String repositoryId = matcher.group(1).trim();
                    String repositoryName = matcher.group(2).trim();
                    var bambooRepository = new BambooRepositoryDTO(Long.parseLong(repositoryId), repositoryName);
                    list.add(bambooRepository);
                }
                count++;
            }
        }

        return list;
    }
}
