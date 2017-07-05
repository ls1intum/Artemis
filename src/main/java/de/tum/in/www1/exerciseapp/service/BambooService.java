package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.BuildLogEntry;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Repository;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.exception.BambooException;
import de.tum.in.www1.exerciseapp.exception.GitException;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.swift.bamboo.cli.BambooClient;
import org.swift.common.cli.CliClient;

import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
@Profile("bamboo")
public class BambooService implements ContinuousIntegrationService {

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
    GitService gitService;

    @Inject
    private ResultRepository resultRepository;

    @Override
    public String copyBuildPlan(String baseBuildPlanId, String wantedPlanKey) {
        wantedPlanKey = cleanPlanKey(wantedPlanKey);
        clonePlan(
            getProjectKeyFromBuildPlanId(baseBuildPlanId),
            getPlanKeyFromBuildPlanId(baseBuildPlanId),
            wantedPlanKey);
        // TODO: This should be returned by clone method instead of being built here
        return getProjectKeyFromBuildPlanId(baseBuildPlanId) + "-" + wantedPlanKey;
    }

    @Override
    public void configureBuildPlan(String buildPlanId, URL repositoryUrl, String planKey) {
        // TODO: planKey not needed - remove from method signature?
        updatePlanRepository(
            getProjectKeyFromBuildPlanId(buildPlanId),
            getPlanKeyFromBuildPlanId(buildPlanId),
            "Assignment", // TODO
            getProjectKeyFromUrl(repositoryUrl),
            getRepositorySlugFromUrl(repositoryUrl)
        );
        enablePlan(getProjectKeyFromBuildPlanId(buildPlanId), getPlanKeyFromBuildPlanId(buildPlanId));

        // Empty commit - Bamboo bug workaround

        try {
            Repository repo = gitService.getOrCheckoutRepository(repositoryUrl);
            gitService.commitAndPush(repo, "Setup");
            gitService.deleteLocalRepository(repo);
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new GitException("Git error while doing empty commit");
        } catch (IOException e) {
            e.printStackTrace();
            throw new GitException("IOError while doing empty commit");
        }


    }

    @Override
    public void deleteBuildPlan(String buildPlanId) {
        deletePlan(getProjectKeyFromBuildPlanId(buildPlanId), getPlanKeyFromBuildPlanId(buildPlanId));
    }

    @Override
    public BuildStatus getBuildStatus(Participation participation) {
        Map<String, Boolean> status = retrieveBuildStatus(participation.getBuildPlanId());
        if (status.get("isActive") && !status.get("isBuilding")) {
            return BuildStatus.QUEUED;
        } else if (status.get("isActive") && status.get("isBuilding")) {
            return BuildStatus.BUILDING;
        } else {
            return BuildStatus.INACTIVE;
        }
    }

    @Override
    public Map<String, Object> getLatestBuildResultDetails(Participation participation) {
        Map<String, Object> details = retrieveLatestBuildResultDetails(participation.getBuildPlanId());
        return details;
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(Participation participation) {
        return retrieveLatestBuildLogs(participation.getBuildPlanId());
    }


    @Override
    public URL getBuildPlanWebUrl(Participation participation) {
        try {
            return new URL(BAMBOO_SERVER + "/browse/" + participation.getBuildPlanId().toUpperCase());
        } catch (MalformedURLException e) {
            log.error("Couldn't construct build plan web URL");
        }
        return BAMBOO_SERVER;
    }

    /**
     * Clones an existing Bamboo plan.
     *
     * @param baseProject The Bamboo project in which the plan is contained.
     * @param basePlan    The plan's name.
     * @param name        The name to give the cloned plan.
     */
    public CliClient.ExitCode clonePlan(String baseProject, String basePlan, String name) throws BambooException {
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
        if (!exitCode.equals(CliClient.ExitCode.SUCCESS)) {
            log.error("Error while cloning plan");
            throw new BambooException("Something went wrong while cloning build plan");
        }
        return exitCode;
    }

    /**
     * Enables the given build plan.
     *
     * @param projectKey
     * @param planKey
     * @return
     */
    public CliClient.ExitCode enablePlan(String projectKey, String planKey) throws BambooException {
        final BambooClient client = new BambooClient();
        String[] args = new String[]{"--action", "enablePlan",
            "--plan", projectKey + "-" + planKey,
            "-s", BAMBOO_SERVER.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD,
        };
        CliClient.ExitCode exitCode = client.doWork(args);
        if (!exitCode.equals(CliClient.ExitCode.SUCCESS)) {
            log.error("Error while enabling plan");
            throw new BambooException("Something went wrong while enabling build plan");
        }
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
    public CliClient.ExitCode updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository) throws BambooException {
        final BambooClient client = new BambooClient();
        client.setDebug(true);
        client.setLogging(true);
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
        if (!exitCode.equals(CliClient.ExitCode.SUCCESS)) {
            log.error("Error while updating build plan repository. ExitCode: "+exitCode);
            throw new BambooException("Something went wrong while updating build plan repository. ExitCode: "+exitCode);
        }
        return exitCode;
    }

    /**
     * Deletes the given plan.
     *
     * @param projectKey
     * @param planKey
     * @return
     */
    public CliClient.ExitCode deletePlan(String projectKey, String planKey) {
        final BambooClient client = new BambooClient();
        String[] args = new String[]{
            "--action", "deletePlan",
            "--plan", projectKey + "-" + planKey,
            "-s", BAMBOO_SERVER.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD
        };
        CliClient.ExitCode exitCode = client.doWork(args);
        log.info("Deleting plan exited with code " + exitCode);
        return exitCode;
    }

    /**
     * Retrieves the latest build result for the given plan key and saves it as result.
     * It checks if the build result is the current one. If not, it waits for a configurable delay and then tries again.
     *
     * @param participation
     */
    @Override
    public void onBuildCompleted(Participation participation) {
        log.info("Retrieving build result...");
        Boolean isOldBuildResult = true;
        Map buildResults = new HashMap<>();
        try {
            buildResults = retrieveLatestBuildResult(participation.getBuildPlanId());
            isOldBuildResult = TimeUnit.SECONDS.toMillis(ZonedDateTime.now().toEpochSecond() - ((ZonedDateTime) buildResults.get("buildCompletedDate")).toEpochSecond()) > RESULT_RETRIEVAL_DELAY;
        } catch (Exception e) {
            // First try failed.
        }

        if (isOldBuildResult) {
            log.info("It seems we got an old build result from Bamboo. Waiting " + RESULT_RETRIEVAL_DELAY / 1000 + "s to retrieve build result...");
            try {
                Thread.sleep(RESULT_RETRIEVAL_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("Retrieving build result (second try)...");
            buildResults = retrieveLatestBuildResult(participation.getBuildPlanId());
        }

        Result result = new Result();
        result.setBuildSuccessful((boolean) buildResults.get("buildSuccessful"));
        result.setResultString((String) buildResults.get("buildTestSummary"));
        result.setBuildCompletionDate((ZonedDateTime) buildResults.get("buildCompletedDate"));
        result.setScore(calculateScoreForResult(result));
        result.setBuildArtifact(buildResults.containsKey("artifact"));
        result.setParticipation(participation);
        resultRepository.save(result);
    }

    /**
     * Calculates the score for a result. Therefore is uses the number of successful tests in the latest build.
     *
     * @param result
     * @return
     */
    private Long calculateScoreForResult(Result result) {

        if (result.isBuildSuccessful()) {
            return (long) 100;
        }

        if (result.getResultString() != null && !result.getResultString().isEmpty()) {

            Pattern p = Pattern.compile("^([0-9]+) of ([0-9]+) failed");
            Matcher m = p.matcher(result.getResultString());

            if (m.find()) {
                float failedTests = Float.parseFloat(m.group(1));
                float totalTests = Float.parseFloat(m.group(2));
                float score = (totalTests - failedTests) / totalTests;
                return (long) (score * 100);
            }

        }

        return (long) 0;
    }


    /**
     * Performs a request to the Bamboo REST API to retrive the latest result for the given plan.
     *
     * @param planKey the key of the plan for which to retrieve the latest result
     * @return a map containing the following data:
     * - buildSuccessful:       if the build was successful
     * - buildTestSummary:      a string generated by Bamboo summarizing the build result
     * - buildCompletedDate:    the completion date of the build
     */
    private Map<String, Object> retrieveLatestBuildResult(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER + "/rest/api/latest/result/" + planKey.toUpperCase() + "/latest.json?expand=testResults,artifacts",
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

            if(response.getBody().containsKey("artifacts")) {
                Map<String, Object> artifacts = (Map<String, Object>)response.getBody().get("artifacts");
                if((int)artifacts.get("size") > 0 && artifacts.containsKey("artifact")) {
                    Map<String, Object> firstArtifact = (Map<String, Object>) ((ArrayList<Map>) artifacts.get("artifact")).get(0);
                    String artifact = (String) ((Map<String, Object>) firstArtifact.get("link")).get("href");
                    result.put("artifact", artifact);
                }
            }

            return result;
        }
        return null;
    }

    /**
     * Performs a request to the Bamboo REST API to retrieve details on the failed tests of the latest build.
     * <p>
     * TODO: This currently just gets the failed tests of the default job!
     *
     * @param planKey the key of the plan for which to retrieve the details
     * @return
     */
    public Map<String, Object> retrieveLatestBuildResultDetails(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = null;
        try {
            // https://bamboobruegge.in.tum.de/rest/api/latest/result/EIST16W1-TESTEXERCISEAPP-JOB1/latest.json?expand=testResults.failedTests.testResult.errors
            response = restTemplate.exchange(
                BAMBOO_SERVER + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json?expand=testResults.failedTests.testResult.errors",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build result details", e);
        }
        if (response != null) {
            Map<String, Object> result = new HashMap<>();
            List resultDetails = (List) ((Map) ((Map) response.getBody().get("testResults")).get("failedTests")).get("testResult");
            result.put("details", resultDetails);
            return result;
        }
        return null;
    }


    /**
     * Performs a request to the Bamboo REST API to retrieve the build log of the latest build.
     *
     * @param planKey
     * @return
     */
    public List<BuildLogEntry> retrieveLatestBuildLogs(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json?expand=logEntries",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build result logs", e);
        }

        ArrayList logs = new ArrayList<BuildLogEntry>();

        if (response != null) {
            for (HashMap<String, Object> logEntry : (List<HashMap>) ((Map) response.getBody().get("logEntries")).get("logEntry")) {
                Instant i = Instant.ofEpochMilli((long) logEntry.get("date"));
                ZonedDateTime logDate = ZonedDateTime.ofInstant(i, ZoneId.systemDefault());
                BuildLogEntry log = new BuildLogEntry(logDate, (String) logEntry.get("log"));
                logs.add(log);
            }
        }
        return logs;
    }


    /**
     * Gets the latest available artifact for the given plan key
     *
      * @param participation
     * @return
     */
    public ResponseEntity retrieveLatestArtifact(Participation participation) {
        String planKey = participation.getBuildPlanId();
        Map<String, Object> latestResult = retrieveLatestBuildResult(planKey);
        if(latestResult.containsKey("artifact")) {
            return retrievArtifactPage((String)latestResult.get("artifact"));
        } else {
            throw new BambooException("No build artifact available for this plan");
        }


    }

    /**
     * Gets the content from a Bamboo artifact link
     * Follows links on HTML directory pages, if necessary
     *
     * @param url
     * @return
     */
    private ResponseEntity retrievArtifactPage(String url) throws BambooException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<byte[]> response = null;

        try {
            response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build artifact", e);
            throw new BambooException("HttpError while retrieving build artifact");
        }


        if(response.getHeaders().containsKey("Content-Type") && response.getHeaders().get("Content-Type").get(0).equals("text/html")) {
            String html = new String(response.getBody(), StandardCharsets.UTF_8);
            // HTML directory page
            Pattern p = Pattern.compile("href=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(html);
            if (m.find()) {
                url = m.group(1);
                return retrievArtifactPage(BAMBOO_SERVER + url);
            } else {
                throw new BambooException("No artifact link found on artifact page");
            }

        } else {
            // Actual artifact file
            return response;
        }

    }

    /**
     * Retrieves the current build status of the given plan.
     *
     * @param planKey the key of the plan for which to retrieve the status
     * @returna map containing the following data:
     * - isActive: true if the plan is queued or building
     * - isBuilding: true if the plan is building
     */
    public Map<String, Boolean> retrieveBuildStatus(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER + "/rest/api/latest/plan/" + planKey.toUpperCase() + ".json",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build status", e);
        }
        if (response != null) {
            Map<String, Boolean> result = new HashMap<>();
            boolean isActive = (boolean) response.getBody().get("isActive");
            boolean isBuilding = (boolean) response.getBody().get("isBuilding");
            result.put("isActive", isActive);
            result.put("isBuilding", isBuilding);
            return result;
        }
        return null;
    }

    /**
     * Check if the given build plan is valid and accessible on Bamboo.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     * @return
     */
    @Override
    public Boolean buildPlanIdIsValid(String buildPlanId) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER + "/rest/api/latest/plan/" + buildPlanId.toUpperCase(),
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    private String buildSshRepositoryUrl(String project, String slug) {
        final int sshPort = 7999;

        return "ssh://git@" + BITBUCKET_SERVER.getHost() + ":" + sshPort + "/" + project.toLowerCase() + "/" + slug.toLowerCase() + ".git";
    }

    private String getProjectKeyFromBuildPlanId(String buildPlanId) {
        return buildPlanId.split("-")[0];
    }

    private String getPlanKeyFromBuildPlanId(String buildPlanId) {
        return buildPlanId.split("-")[1];
    }

    private String getProjectKeyFromUrl(URL repositoryUrl) {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        return repositoryUrl.getFile().split("/")[2];
    }


    private String cleanPlanKey(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private String getRepositorySlugFromUrl(URL repositoryUrl) {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String repositorySlug = repositoryUrl.getFile().split("/")[3];
        if (repositorySlug.endsWith(".git")) {
            repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
        }
        return repositorySlug;
    }
}
