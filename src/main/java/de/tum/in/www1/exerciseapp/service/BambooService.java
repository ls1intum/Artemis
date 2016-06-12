package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.swift.bamboo.cli.BambooClient;
import org.swift.common.cli.CliClient;

import javax.inject.Inject;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class BambooService {

    private final Logger log = LoggerFactory.getLogger(BambooService.class);

    @Value("${exerciseapp.bamboo.url}")
    private URL BAMBOO_SERVER;

    @Value("${exerciseapp.bamboo.bitbucket-application-link-id}")
    private String BITBUCKET_APPLICATION_LINK_ID;

    @Value("${exerciseapp.bamboo.user}")
    private String BAMBOO_USER;

    @Value("${exerciseapp.bamboo.password}")
    private String BAMBOO_PASSWORD;

    @Value("${exerciseapp.bitbucket.url}")
    private URL BITBUCKET_SERVER;

    @Value("${exerciseapp.result-retrieval-delay}")
    private int RESULT_RETRIEVAL_DELAY = 10000;

    @Inject
    ParticipationService participationService;

    @Autowired
    ResultRepository resultRepository;

    /**
     * Clones an existing Bamboo plan.
     *
     * @param baseProject The Bamboo project in which the plan is contained.
     * @param basePlan    The plan's name.
     * @param name        The name to give the cloned plan.
     */
    public CliClient.ExitCode clonePlan(String baseProject, String basePlan, String name) {
        final BambooClient client = new BambooClient();
        String[] args = new String[]{"--action", "clonePlan",
            "--plan", baseProject + "-" + basePlan,
            "--toPlan", baseProject + "-" + name,
            "--name", baseProject + "-" + name,
            "--disable",
            "-s", BAMBOO_SERVER.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD,
        };
        CliClient.ExitCode exitCode = client.doWork(args);
        log.info("Cloning plan exited with code " + exitCode);
        return exitCode;
    }

    /**
     * Enables the given build plan
     *
     * @param projectKey
     * @param planKey
     * @return
     */
    public CliClient.ExitCode enablePlan(String projectKey, String planKey) {
        final BambooClient client = new BambooClient();
        String[] args = new String[]{"--action", "enablePlan",
            "--plan", projectKey + "-" + planKey,
            "-s", BAMBOO_SERVER.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD,
        };
        CliClient.ExitCode exitCode = client.doWork(args);
        log.info("Enabling plan exited with code " + exitCode);
        return exitCode;
    }

    /**
     * Updates the configured repository for a given plan to the given Bitbucket Server repository.
     *
     * @param bambooProject        The key of the Bamboo plan's project, e.g. 'EIST16W1'.
     * @param bambooPlan           The plan key, which is usually the name, e.g. 'ga56hur'.
     * @param bambooRepositoryName The name of the configured repository in the Bamboo plan.
     * @param bitbucketProject     The key for the Bitbucket Server (formerly Stash) project to which we want to update the plan.
     * @param bitbucketRepository  The name/slug for the Bitbucket Server (formerly Stash) repository to which we want to update the plan.
     */
    public CliClient.ExitCode updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository) {
        final BambooClient client = new BambooClient();
        String[] args = new String[]{
            "--action", "updateRepository",
            "--plan", bambooProject + "-" + bambooPlan,
            "--repository", bambooRepositoryName,
            "--repositoryKey", "STASH",
            "--field1", "repository.stash.projectKey", "--value1", bitbucketProject,
            "--field2", "repository.stash.repositoryId", "--value2", "2499", // Doesn't seem to be required
            "--field3", "repository.stash.repositorySlug", "--value3", bitbucketRepository,
            "--field4", "repository.stash.repositoryUrl", "--value4", buildSshRepositoryUrl(bitbucketProject, bitbucketRepository), // e.g. "ssh://git@repobruegge.in.tum.de:7999/madm/helloworld.git"
            "--field5", "repository.stash.server", "--value5", BITBUCKET_APPLICATION_LINK_ID,
            "--field6", "repository.stash.branch", "--value6", "master",
            "-s", BAMBOO_SERVER.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD
        };
        CliClient.ExitCode exitCode = client.doWork(args);
        log.info("Updating plan repository exited with code " + exitCode);
        return exitCode;
    }

    public CliClient.ExitCode deletePlan(String project, String plan) {
        final BambooClient client = new BambooClient();
        String[] args = new String[]{
            "--action", "deletePlan",
            "--plan", project + "-" + plan,
            "-s", BAMBOO_SERVER.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD
        };
        CliClient.ExitCode exitCode = client.doWork(args);
        log.info("Deleting plan exited with code " + exitCode);
        return exitCode;
    }

    @Async
    public void retrieveAndSaveBuildResult(String planKey, Participation participation) {
        log.info("Waiting " + RESULT_RETRIEVAL_DELAY / 1000 + "s to retrieve build result...");
        try {
            Thread.sleep(RESULT_RETRIEVAL_DELAY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("Retrieving build result...");
        Map buildResults = retrieveBuildResults(planKey);
        Result result = new Result();
        result.setBuildSuccessful((boolean) buildResults.get("buildSuccessful"));
        result.setResultString((String) buildResults.get("buildTestSummary"));
        result.setBuildCompletionDate((ZonedDateTime) buildResults.get("buildCompletedDate"));
        result.setParticipation(participation);
        resultRepository.save(result);
        // TODO: This does not have any effect yet, since the results are retrieved from the repository ordered by id
        participation.getResults().add(result);
        participationService.save(participation);
    }

    private Map<String, Object> retrieveBuildResults(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER + "/rest/api/latest/result/" + planKey + "/latest.json?expand=testResults",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving results", e);
        }
        if (response != null) {
            Map<String, Object> result = new HashMap<>();
            boolean buildSuccessful = (boolean) response.getBody().get("successful");
            result.put("buildSuccessful", buildSuccessful);
            String buildTestSummary = (String) response.getBody().get("buildTestSummary");
            result.put("buildTestSummary", buildTestSummary);
            String dateString = (String) response.getBody().get("buildCompletedDate");
            ZonedDateTime buildCompletedDate = ZonedDateTime.parse(dateString);
            result.put("buildCompletedDate", buildCompletedDate);
            return result;
        }
        return null;
    }

    private String buildSshRepositoryUrl(String project, String slug) {
        final int sshPort = 7999;
        return "ssh://git@" + BITBUCKET_SERVER.getHost() + ":" + sshPort + "/" + project.toLowerCase() + "/" + slug.toLowerCase() + ".git";
    }
}
