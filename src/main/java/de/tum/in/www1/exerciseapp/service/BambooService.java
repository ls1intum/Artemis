package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
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

    @Value("${artemis.bamboo.url}")
    private URL BAMBOO_SERVER_URL;

    @Value("${artemis.bamboo.bitbucket-application-link-id}")
    private String BITBUCKET_APPLICATION_LINK_ID;

    @Value("${artemis.bamboo.user}")
    private String BAMBOO_USER;

    @Value("${artemis.bamboo.password}")
    private String BAMBOO_PASSWORD;

    @Value("${artemis.bitbucket.url}")
    private URL BITBUCKET_SERVER;

    @Value("${artemis.result-retrieval-delay}")
    private int RESULT_RETRIEVAL_DELAY = 10000;

    //Never use this field directly, always call getBambooClient()
    private BambooClient bambooClient;

    private final GitService gitService;
    private final ResultRepository resultRepository;

    public BambooService(GitService gitService, ResultRepository resultRepository) {
        this.gitService = gitService;
        this.resultRepository = resultRepository;
    }

    private BambooClient getBambooClient() {
        if (bambooClient == null) {
            bambooClient = new BambooClient();
            //setup the Bamboo Client to use the correct username and password

            String[] args = new String[]{
                "-s", BAMBOO_SERVER_URL.toString(),
                "--user", BAMBOO_USER,
                "--password", BAMBOO_PASSWORD,
            };

            bambooClient.doWork(args); //only invoke this to set server address, username and password so that the following action will work
        }
        return bambooClient;
    }

    @Override
    public String copyBuildPlan(String baseBuildPlanId, String wantedPlanKey) {
        wantedPlanKey = cleanPlanKey(wantedPlanKey);
        try {
            return clonePlan(getProjectKeyFromBuildPlanId(baseBuildPlanId), getPlanKeyFromBuildPlanId(baseBuildPlanId), wantedPlanKey);
        }
        catch(BambooException bambooException) {
            if (bambooException.getMessage().contains("already exists")) {
                log.info("Build Plan already exists. Going to recover build plan information...");
                return getProjectKeyFromBuildPlanId(baseBuildPlanId) + "-" + wantedPlanKey;
            }
            else throw bambooException;
        }
    }

    @Override
    public void configureBuildPlan(String buildPlanId, URL repositoryUrl, String planKey) {
        updatePlanRepository(
            getProjectKeyFromBuildPlanId(buildPlanId),
            getPlanKeyFromBuildPlanId(buildPlanId),
            "Assignment",
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
        return retrieveLatestBuildResultDetails(participation.getBuildPlanId());
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(Participation participation) {
        return retrieveLatestBuildLogs(participation.getBuildPlanId());
    }


    @Override
    public URL getBuildPlanWebUrl(Participation participation) {
        try {
            return new URL(BAMBOO_SERVER_URL + "/browse/" + participation.getBuildPlanId().toUpperCase());
        } catch (MalformedURLException e) {
            log.error("Couldn't construct build plan web URL");
        }
        return BAMBOO_SERVER_URL;
    }

    /**
     * Clones an existing Bamboo plan.
     *
     * @param baseProject The Bamboo project in which the plan is contained.
     * @param basePlan    The plan's name.
     * @param name        The name to give the cloned plan.
     * @return            The name of the new build plan
     */
    public String clonePlan(String baseProject, String basePlan, String name) throws BambooException {

        String toPlan = baseProject + "-" + name;
        try {
            log.info("Clone build plan " + baseProject + "-" + basePlan + " to " + toPlan);
            String message = getBambooClient().getPlanHelper().clonePlan(baseProject + "-" + basePlan, toPlan, toPlan, "", "", true);
            log.info("Clone build plan " + toPlan + " was successful." + message);
        } catch (CliClient.ClientException clientException) {
            log.error(clientException.getMessage(), clientException);
            if (clientException.getMessage().contains("already exists")) {
                throw new BambooException(clientException.getMessage());
            }
        } catch (CliClient.RemoteRestException e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while cloning build plan", e);
        }
        return toPlan;
    }

    /**
     * Enables the given build plan.
     *
     * @param projectKey
     * @param planKey
     * @return
     */
    public String enablePlan(String projectKey, String planKey) throws BambooException {

        try {
            log.info("Enable build plan " + projectKey + "-" + planKey);
            String message = getBambooClient().getPlanHelper().enablePlan(projectKey + "-" + planKey, true);
            log.info("Enable build plan " + projectKey + "-" + planKey + " was successful. " + message);
            return message;
        } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while enabling the build plan", e);
        }
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
    public String updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String bitbucketProject, String bitbucketRepository) throws BambooException {

        String[] args = new String[]{
            "--field1", "repository.stash.projectKey", "--value1", bitbucketProject,
            "--field2", "repository.stash.repositoryId", "--value2", "2499", // Doesn't seem to be required
            "--field3", "repository.stash.repositorySlug", "--value3", bitbucketRepository,
            "--field4", "repository.stash.repositoryUrl", "--value4", buildSshRepositoryUrl(bitbucketProject, bitbucketRepository), // e.g. "ssh://git@repobruegge.in.tum.de:7999/madm/helloworld.git"
            "--field5", "repository.stash.server", "--value5", BITBUCKET_APPLICATION_LINK_ID,
            "--field6", "repository.stash.branch", "--value6", "master",
            "-s", BAMBOO_SERVER_URL.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD
        };
        //workaround to pass additional fields
        getBambooClient().doWork(args);

        try {
            log.info("Update plan repository for build plan " + bambooProject + "-" + bambooPlan);
            String message = getBambooClient().getRepositoryHelper().addOrUpdateRepository(bambooRepositoryName, null, bambooProject + "-" + bambooPlan, "STASH", null, false, true);
            log.info("Update plan repository for build plan " + bambooProject + "-" + bambooPlan + " was successful." + message);
            return message;
        } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while updating the plan repository", e);
        }
    }

    /**
     * Deletes the given plan.
     *
     * @param projectKey
     * @param planKey
     * @return
     */
    public String deletePlan(String projectKey, String planKey) {
        try {
            log.info("Delete build plan " + projectKey + "-" + planKey);
            String message = getBambooClient().getPlanHelper().deletePlan(projectKey + "-" + planKey);
            log.info("Delete build plan was successful. " + message);
            return message;
        } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while deleting the build plan", e);
        }
    }

    /**
     * Retrieves the latest build result for the given plan key and saves it as result.
     * It checks if the build result is the current one. If not, it waits for a configurable delay and then tries again.
     *
     * @param participation
     */

    //ToDo configure saving of the feedback
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
        result.setSuccessful((boolean) buildResults.get("successful"));
        result.setResultString((String) buildResults.get("buildTestSummary"));
        result.setCompletionDate((ZonedDateTime) buildResults.get("buildCompletedDate"));
        result.setScore(calculateScoreForResult(result));

        Map buildResultDetails = retrieveLatestBuildResultDetails(participation.getBuildPlanId());
        result.setFeedbacks(createFeedbacksForResult(buildResultDetails));

        result.setBuildArtifact(buildResults.containsKey("artifact"));
        result.setParticipation(participation);
        resultRepository.save(result);
    }

    /*
   * Uses the retreiveLatestBuildResultDetails in order to create feebacks from the error to give the user preciser error messages
   *
   *@param buildResultDetails returned build result details from the rest api of bamboo
    *
    * @return a Set of feedbacks which can directly be stored int a result
    */
    @Override
    public HashSet<Feedback> createFeedbacksForResult(Map<String, Object> buildResultDetails){
        if(buildResultDetails == null){
            return null;
        }
        HashSet<Feedback> feedbacks = new HashSet<>();

        for(Object buildError : buildResultDetails.values()) {
            Feedback feedback = new Feedback();

            //converting build results from bamboo api call to feedbacks
            //in Text both class name and method name is stored
            //detail text will have the stored error message
            String className = (String)((Map)buildError).get("className");
            String methodName = (String)((Map)buildError).get("methodName");
            String errorMessage = (String)((Map)(((Map)(((Map)buildError).get("errors"))).get("error"))).get("message");

            //Splitting string at the first linebreak to only get the first line of the Exception
            errorMessage = errorMessage.split("\\n", 2)[0];

            feedback.setText("In the class: " + className + ",in method: " + methodName + " the following error occured:");
            feedback.setDetailText(errorMessage);

            feedbacks.add(feedback);
        }
        return feedbacks;
    }

    /**
     * Calculates the score for a result. Therefore is uses the number of successful tests in the latest build.
     *
     * @param result
     * @return
     */
    private Long calculateScoreForResult(Result result) {

        if (result.isSuccessful()) {
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
     * - successful:       if the build was successful
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
                BAMBOO_SERVER_URL + "/rest/api/latest/result/" + planKey.toUpperCase() + "/latest.json?expand=testResults,artifacts",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving results", e);
        }
        if (response != null) {
            Map<String, Object> result = new HashMap<>();
            boolean successful = (boolean) response.getBody().get("successful");
            result.put("successful", successful);
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
                BAMBOO_SERVER_URL + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json?expand=testResults.failedTests.testResult.errors",
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
                BAMBOO_SERVER_URL + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json?expand=logEntries",
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
        // If the build has an artifact, the resppnse contains an artifact key.
        // It seems this key is only available if the "Share" checkbox in Bamboo was used.
        if(latestResult.containsKey("artifact")) {
            // The URL points to the directory. Bamboo returns an "Index of" page.
            // Recursively walk through the responses until we get the actual artifact.
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
        ResponseEntity<byte[]> response;

        try {
            response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build artifact", e);
            throw new BambooException("HttpError while retrieving build artifact");
        }

        if(response.getHeaders().containsKey("Content-Type") && response.getHeaders().get("Content-Type").get(0).equals("text/html")) {
            // This is an "Index of" HTML page.
            String html = new String(response.getBody(), StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("href=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(html);
            if (m.find()) {
                url = m.group(1);
                // Recursively walk through the responses until we get the actual artifact.
                return retrievArtifactPage(BAMBOO_SERVER_URL + url);
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
                BAMBOO_SERVER_URL + "/rest/api/latest/plan/" + planKey.toUpperCase() + ".json",
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
                BAMBOO_SERVER_URL + "/rest/api/latest/plan/" + buildPlanId.toUpperCase(),
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
