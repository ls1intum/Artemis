package de.tum.cit.aet.artemis.service.connectors.aeolus;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.SOLUTION_REPO_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.TEST_REPO_NAME;
import static de.tum.cit.aet.artemis.domain.enumeration.AeolusTarget.JENKINS;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.AeolusTarget;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.service.InternalUrlService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.dto.AeolusGenerationResponseDTO;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;

/**
 * Service for publishing custom build plans using Aeolus
 */
@Service
@Profile("aeolus")
public class AeolusBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(AeolusBuildPlanService.class);

    private final Optional<InternalUrlService> internalUrlService;

    private final RestTemplate restTemplate;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    @Value("${aeolus.token:#{null}}")
    private String token;

    @Value("${artemis.continuous-integration.token:#{null}}")
    private String ciToken;

    @Value("${artemis.continuous-integration.password:#{null}}")
    private String ciPassword;

    @Value("${artemis.continuous-integration.user:#{null}}")
    private String ciUsername;

    @Value("${artemis.continuous-integration.url:#{null}}")
    private String ciUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor for the AeolusBuildPlanService
     *
     * @param restTemplate       the rest template to use
     * @param internalUrlService the internal URL service
     */
    public AeolusBuildPlanService(@Qualifier("aeolusRestTemplate") RestTemplate restTemplate, Optional<InternalUrlService> internalUrlService) {
        this.restTemplate = restTemplate;
        this.internalUrlService = internalUrlService;
    }

    /**
     * Returns the internal URL of the CI server
     *
     * @return the internal URL of the CI server
     */
    private String getCiUrl() {
        if (internalUrlService.isEmpty()) {
            return ciUrl;
        }
        return internalUrlService.get().toInternalCiUrl(ciUrl);
    }

    /**
     * Returns the internal VCS URL to the CI server
     *
     * @param url the URL of the repository
     * @return the internal URL to the CI server
     */
    private String getVCSUrl(VcsRepositoryUri url) {
        if (internalUrlService.isEmpty()) {
            return url.toString();
        }
        return internalUrlService.get().toInternalVcsUrl(url).toString();
    }

    /**
     * Returns the credentials for the CI server based on the target
     *
     * @param target the target to get the credentials for
     * @return the credentials for the CI server based on the target
     */
    private String getCredentialsBasedOnTarget(AeolusTarget target) {
        if (target == JENKINS) {
            return ciPassword;
        }
        return null;
    }

    /**
     * Publishes a build plan using Aeolus
     *
     * @param windfile the build plan to publish
     * @param target   the target to publish to jenkins
     * @return the key of the published build plan
     */
    public String publishBuildPlan(Windfile windfile, AeolusTarget target) throws JsonProcessingException {
        String url = getCiUrl();
        String buildPlan = objectMapper.writeValueAsString(windfile);
        if (url == null) {
            log.error("Could not publish build plan {} to Aeolus target {}, no CI URL configured", buildPlan, target);
            return null;
        }
        String requestUrl = aeolusUrl + "/publish/" + target.getName();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);
        Map<String, Object> jsonObject = new HashMap<>();
        HttpHeaders headers = getBaseHttpHeaders();

        jsonObject.put("username", token == null ? ciUsername : null);
        jsonObject.put("token", token == null ? getCredentialsBasedOnTarget(target) : null);

        jsonObject.put("url", url);
        jsonObject.put("windfile", buildPlan);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(jsonObject, headers);
        try {
            ResponseEntity<AeolusGenerationResponseDTO> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, entity, AeolusGenerationResponseDTO.class);

            if (response.getBody() != null) {
                return response.getBody().key();
            }
        }
        catch (RestClientException e) {
            log.error("Error while publishing build plan {} to Aeolus target {}", buildPlan, target, e);
        }
        return null;
    }

    /**
     * Generates a build script for a programming exercise using Aeolus
     *
     * @param windfile the build plan to generate the build script for
     * @param target   the target to generate the build script for jenkins or cli
     * @return the generated build script
     */
    public String generateBuildScript(Windfile windfile, AeolusTarget target) throws JsonProcessingException {
        String buildPlan = objectMapper.writeValueAsString(windfile);
        String requestUrl = aeolusUrl + "/generate/" + target.getName();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);

        HttpEntity<String> entity = new HttpEntity<>(buildPlan, getBaseHttpHeaders());
        try {
            ResponseEntity<AeolusGenerationResponseDTO> response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, entity, AeolusGenerationResponseDTO.class);
            if (response.getBody() != null) {
                return response.getBody().result();
            }
        }
        catch (RestClientException e) {
            log.error("Error while generating build script for build plan {}", buildPlan, e);
        }
        return null;
    }

    private HttpHeaders getBaseHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
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
        Map<String, AeolusRepository> repositoryMap = new HashMap<>();
        repositoryMap.put(ASSIGNMENT_REPO_NAME,
                new AeolusRepository(getVCSUrl(repositoryUri), branch, ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage)));
        if (checkoutSolutionRepository) {
            repositoryMap.put(SOLUTION_REPO_NAME, new AeolusRepository(getVCSUrl(solutionRepositoryUri), branch,
                    ContinuousIntegrationService.RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage)));
        }
        repositoryMap.put(TEST_REPO_NAME,
                new AeolusRepository(getVCSUrl(testRepositoryUri), branch, ContinuousIntegrationService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage)));
        for (var auxRepo : auxiliaryRepositories) {
            repositoryMap.put(auxRepo.name(), new AeolusRepository(auxRepo.repositoryUri().toString(), branch, auxRepo.name()));
        }
        return repositoryMap;
    }
}
