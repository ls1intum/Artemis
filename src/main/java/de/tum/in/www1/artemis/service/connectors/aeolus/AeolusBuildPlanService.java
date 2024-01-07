package de.tum.in.www1.artemis.service.connectors.aeolus;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.SOLUTION_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

import java.net.URL;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationBuildPlanException;
import de.tum.in.www1.artemis.service.connectors.aeolus.dto.AeolusGenerationResponseDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooInternalUrlService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;

/**
 * Service for publishing custom build plans using Aeolus, currently supports Bamboo
 */
@Service
@Profile("aeolus")
public class AeolusBuildPlanService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AeolusBuildPlanService.class);

    private final Optional<BambooInternalUrlService> bambooInternalUrlService;

    private final RestTemplate restTemplate;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    @Value("${artemis.continuous-integration.token}")
    private String ciToken;

    @Value("${artemis.continuous-integration.user}")
    private String ciUsername;

    @Value("${artemis.continuous-integration.url}")
    private String ciUrl;

    /**
     * Constructor for the AeolusBuildPlanService
     *
     * @param restTemplate             the rest template to use
     * @param bambooInternalUrlService the internal URL service for Bamboo
     */
    public AeolusBuildPlanService(@Qualifier("aeolusRestTemplate") RestTemplate restTemplate, Optional<BambooInternalUrlService> bambooInternalUrlService) {
        this.restTemplate = restTemplate;
        this.bambooInternalUrlService = bambooInternalUrlService;
    }

    /**
     * Returns the internal URL of the CI server for Bamboo
     *
     * @return the internal URL of the CI server
     */
    private String getCiUrl() {
        return bambooInternalUrlService.map(internalUrlService -> internalUrlService.toInternalCiUrl(ciUrl)).orElse(null);
    }

    /**
     * Publishes a build plan using Aeolus
     *
     * @param windfile the build plan to publish
     * @param target   the target to publish to, either bamboo or jenkins
     * @return the key of the published build plan
     */
    public String publishBuildPlan(Windfile windfile, AeolusTarget target) {
        String url = getCiUrl();
        String buildPlan = new Gson().toJson(windfile);
        if (url == null) {
            LOGGER.error("Could not publish build plan {} to Aeolus target {}, no CI URL configured", buildPlan, target);
            return null;
        }
        String requestUrl = aeolusUrl + "/publish/" + target.getName();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("url", url);
        jsonObject.put("username", ciUsername);
        jsonObject.put("token", ciToken);
        jsonObject.put("windfile", buildPlan);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(jsonObject, null);
        try {
            ResponseEntity<AeolusGenerationResponseDTO> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, entity, AeolusGenerationResponseDTO.class);
            if (response.getBody() != null) {
                return response.getBody().getKey();
            }
        }
        catch (RestClientException e) {
            LOGGER.error("Error while publishing build plan {} to Aeolus target {}", buildPlan, target, e);
        }
        return null;
    }

    /**
     * Generates a build script for a programming exercise using Aeolus
     *
     * @param windfile the build plan to generate the build script for
     * @param target   the target to generate the build script for, either bamboo or jenkins or cli
     * @return the generated build script
     */
    public String generateBuildScript(Windfile windfile, AeolusTarget target) {
        String buildPlan = new Gson().toJson(windfile);
        String requestUrl = aeolusUrl + "/generate/" + target.getName();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);

        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(buildPlan, headers);
        try {
            ResponseEntity<AeolusGenerationResponseDTO> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, entity, AeolusGenerationResponseDTO.class);
            if (response.getBody() != null) {
                return response.getBody().getResult();
            }
        }
        catch (RestClientException e) {
            LOGGER.error("Error while generating build script for build plan {}", buildPlan, e);
        }
        return null;
    }

    /**
     * Creates a map of repositories used in Aeolus to create the checkout task in the custom build plan
     *
     * @param programmingLanguage        the programming language of the exercise
     * @param branch                     the branch of the exercise
     * @param checkoutSolutionRepository whether the solution repository should be checked out (only used in OCAML and Haskell exercises)
     * @param repositoryUri              the uri of the assignment repository
     * @param testRepositoryUri          the uri of the test repository
     * @param solutionRepositoryUri      the uri of the solution repository
     * @param auxiliaryRepositories      List of auxiliary repositories to be included in the build plan
     * @return a map of repositories used in Aeolus to create the checkout task in the custom build plan
     */
    public Map<String, AeolusRepository> createRepositoryMapForWindfile(ProgrammingLanguage programmingLanguage, String branch, boolean checkoutSolutionRepository,
            VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri, VcsRepositoryUri solutionRepositoryUri,
            List<AuxiliaryRepository.AuxRepoNameWithUri> auxiliaryRepositories) {
        if (bambooInternalUrlService.isEmpty()) {
            throw new ContinuousIntegrationBuildPlanException("Internal URL service for Bamboo is not configured");
        }
        Map<String, AeolusRepository> repositoryMap = new HashMap<>();
        repositoryMap.put(ASSIGNMENT_REPO_NAME, new AeolusRepository(bambooInternalUrlService.get().toInternalVcsUrl(repositoryUri).toString(), branch,
                ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage)));
        if (checkoutSolutionRepository) {
            repositoryMap.put(SOLUTION_REPO_NAME, new AeolusRepository(bambooInternalUrlService.get().toInternalVcsUrl(solutionRepositoryUri).toString(), branch,
                    ContinuousIntegrationService.RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage)));
        }
        repositoryMap.put(TEST_REPO_NAME, new AeolusRepository(bambooInternalUrlService.get().toInternalVcsUrl(testRepositoryUri).toString(), branch,
                ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage)));
        for (var auxRepo : auxiliaryRepositories) {
            repositoryMap.put(auxRepo.name(), new AeolusRepository(auxRepo.repositoryUri().toString(), branch, auxRepo.name()));
        }
        return repositoryMap;
    }

    /**
     * Generates a windfile for a programming exercise using Aeolus
     *
     * @param buildPlanKey the key of the build plan to generate the windfile for
     * @return the generated windfile
     */
    public Windfile translateBuildPlan(AeolusTarget target, String buildPlanKey) {
        String requestUrl = aeolusUrl + "/translate/" + target.getName() + "/" + buildPlanKey;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);
        builder.queryParam("result_format", "json");
        builder.queryParam("exclude_repositories", true);
        Map<String, Object> body = new HashMap<>();
        body.put("url", ciUrl);
        body.put("username", ciUsername);
        body.put("token", ciToken);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
            if (response.getBody() != null) {
                return Windfile.deserialize(response.getBody());
            }
        }
        catch (RestClientException | JsonSyntaxException e) {
            LOGGER.error("Error while generating build script for build plan {}", buildPlanKey, e);
        }
        return null;
    }
}
