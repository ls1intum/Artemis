package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.shaded.json.JSONObject;

import de.tum.in.www1.artemis.domain.LspConfig;
import de.tum.in.www1.artemis.domain.LspServerStatus;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

@Service
public class MonacoEditorService {

    private final Logger log = LoggerFactory.getLogger(MonacoEditorService.class);

    @Value("${artemis.monaco.token:#{null}}")
    private Optional<String> integrationServerToken;

    private final HttpClient client;

    private final List<LspServerStatus> lspServersStatus = new ArrayList<>();

    private final ParticipationRepository participationRepository;

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

    private final String lspInitEndpoint = "/init-lsp";

    private final String terminalInitEndpoint = "/init-terminal";

    private final String forwardUpdatesEndpoint = "/update-files";

    private final String forwardRenameEndpoint = "/rename-file";

    private final String forwardRemovalEndpoint = "/remove-file";

    private final String lspHealthEndpoint = "/health";

    public MonacoEditorService(@Value("${artemis.monaco.lsp-servers:#{null}}") List<String> integrationServerUrls, ParticipationRepository participationRepository) {
        this.client = HttpClientBuilder.create().build();
        this.participationRepository = participationRepository;
        integrationServerUrls.forEach(url -> this.lspServersStatus.add(new LspServerStatus(url)));

        ses.scheduleAtFixedRate(() -> refreshLspStatus(true), 0, 5, TimeUnit.MINUTES);
    }

    /**
     * Method that sends a request to a given LSP server, to initialize a participation
     * by cloning its repository locally.
     *
     * @param participation to initialize
     */
    public LspConfig initLsp(Participation participation) throws IOException, LspException {
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }

        String url = this.getIntegrationServerUrl();

        JSONObject lspInitRequest = new JSONObject();
        lspInitRequest.put("repoUrl", programmingParticipation.getRepositoryUrl());

        try {
            HttpResponse response = this.sendRequest(new HttpPost(url + this.lspInitEndpoint), lspInitRequest, false, false);

            if (response.getStatusLine().getStatusCode() != 200) {
                EntityUtils.consumeQuietly(response.getEntity());
                throw new LspException(response.toString());
            }

            LspConfig responseConfig = new ObjectMapper().readValue(response.getEntity().getContent(), LspConfig.class);
            responseConfig.setServerUrl(new URL(this.getIntegrationServerUrl()));

            return responseConfig;
        }
        catch (LspException e) {
            Optional<LspServerStatus> suspectUnhealthy = this.lspServersStatus.stream().filter(status -> status.getUrl().equals(url)).findFirst();
            suspectUnhealthy.ifPresent(this::checkLspServerHealth);
            throw e;
        }
    }

    /**
     * Method used to request the initialization of a dedicated terminal on an external
     * monaco integration server.
     *
     * @param participation the participation used to initialize the terminal with
     * @return The configuration parameters of the initialized terminal
     */
    public LspConfig initTerminal(Participation participation, String serverUrl) throws IOException {
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }
        validateServerUrl(serverUrl);

        String url = serverUrl + this.terminalInitEndpoint;

        JSONObject terminalInitRequest = new JSONObject();
        terminalInitRequest.put("repoUrl", programmingParticipation.getRepositoryUrl());
        terminalInitRequest.put("programmingLanguage", programmingParticipation.getProgrammingExercise().getProgrammingLanguage().name());
        if (programmingParticipation.getProgrammingExercise().getProjectType() != null) {
            terminalInitRequest.put("projectType", programmingParticipation.getProgrammingExercise().getProjectType().name());
        }

        HttpResponse response = this.sendRequest(new HttpPost(url), terminalInitRequest, true, false);

        if (response.getStatusLine().getStatusCode() == 200) {
            LspConfig responseConfig = new ObjectMapper().readValue(response.getEntity().getContent(), LspConfig.class);
            responseConfig.setServerUrl(new URL(this.getIntegrationServerUrl()));

            return responseConfig;
        }
        else if (response.getStatusLine().getStatusCode() == 429) {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new BadRequestAlertException("The server is currently downloading resources for your environment. Please try again in a moment..", "Monaco",
                    "artemisApp.monaco.errors.imageNotReady");
        }
        else {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new LspException("LSP responded with HTTP " + response.getStatusLine().getStatusCode());
        }

    }

    /**
     * Overloaded method retrieving the participation and calling the actual 'forwardFileUpdates" method
     */
    public void forwardFileUpdates(long participationId, List<FileSubmission> fileUpdates, String serverUrl) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);
        forwardFileUpdates(participation, fileUpdates, serverUrl);
    }

    /**
     * Forwards a list of file changes to a given external monaco server.
     *
     * @param participation ProgrammingParticipation of the files to be forwarded
     * @param fileUpdates   List of FileSubmissions to forward
     * @param serverUrl     Url of the external server to send the updates to
     */
    public void forwardFileUpdates(Participation participation, List<FileSubmission> fileUpdates, String serverUrl) {
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }
        validateServerUrl(serverUrl);

        String url = serverUrl + this.forwardUpdatesEndpoint;

        JSONObject filesUpdateRequest = new JSONObject();
        filesUpdateRequest.put("repoUrl", programmingParticipation.getRepositoryUrl());
        filesUpdateRequest.put("changes", fileUpdates);

        try {
            this.sendRequest(new HttpPut(url), filesUpdateRequest, false, true);
        }
        catch (IOException e) {
            this.log.warn("Error while forwarding files updates to external server at '{}'. Reason: {}", serverUrl, e.getMessage());
        }
    }

    /**
     * Forwards a file renaming to a given external monaco server.
     *
     * @param participation ProgrammingParticipation of the files to be forwarded
     * @param fileMove      Entity representing the renaming action
     * @param serverUrl     Url of the external server to send the updates to
     */
    public void forwardFileRename(Participation participation, FileMove fileMove, String serverUrl) {
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }
        validateServerUrl(serverUrl);

        String url = serverUrl + this.forwardRenameEndpoint;

        JSONObject fileRenamingRequest = new JSONObject();
        fileRenamingRequest.put("repoUrl", programmingParticipation.getRepositoryUrl());
        fileRenamingRequest.put("previous", fileMove.currentFilePath());
        fileRenamingRequest.put("new", fileMove.newFilename());

        try {
            this.sendRequest(new HttpPut(url), fileRenamingRequest, false, true);
        }
        catch (IOException e) {
            this.log.warn("Error while forwarding file rename to external server at '{}'. Reason: {}", serverUrl, e.getMessage());
        }
    }

    /**
     * Forwards a file removal to a given external monaco server.
     *
     * @param participation ProgrammingParticipation of the files to be forwarded
     * @param filename      Name of the file that has been removed
     * @param serverUrl     Url of the external server to send the updates to
     */
    public void forwardFileRemoval(Participation participation, String filename, String serverUrl) {
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }
        validateServerUrl(serverUrl);

        String url = serverUrl + this.forwardRemovalEndpoint;

        JSONObject fileRemovalRequest = new JSONObject();
        fileRemovalRequest.put("repoUrl", programmingParticipation.getRepositoryUrl());
        fileRemovalRequest.put("filename", filename);

        try {
            this.sendRequest(new HttpPut(url), fileRemovalRequest, false, true);
        }
        catch (IOException e) {
            this.log.warn("Error while forwarding file removal to external server at '{}'. Reason: {}", serverUrl, e.getMessage());
        }
    }

    /**
     * Creates and sent a request ready to be sent to the given URL
     *
     * @param request   The request object to send
     * @param body      The body of the request
     * @param noTimeout Optional parameter disabling the timeouts used otherwise
     * @return The Http request object
     * @throws IOException on encoding errors
     */
    private HttpResponse sendRequest(HttpEntityEnclosingRequestBase request, JSONObject body, boolean noTimeout, boolean consumeEntity) throws IOException {
        RequestConfig.Builder requestConfig = RequestConfig.custom();

        if (!noTimeout) {
            requestConfig.setConnectionRequestTimeout(20000);
            requestConfig.setConnectTimeout(20000);
            requestConfig.setSocketTimeout(20000);
            request.setConfig(requestConfig.build());
        }

        request.setEntity(new StringEntity(body.toJSONString(), ContentType.create("application/json", "utf-8")));
        appendApiTokenHeader(request);

        HttpResponse response = this.client.execute(request);

        if (consumeEntity) {
            EntityUtils.consumeQuietly(response.getEntity());
        }

        return response;
    }

    /**
     * Appends the 'x-api-token' header to the request with
     * the corresponding base64 econded token
     *
     * @param request to append the API token header to
     */
    private void appendApiTokenHeader(HttpRequest request) {
        integrationServerToken.ifPresent((token) -> {
            request.setHeader("x-api-token", Base64.encodeBase64String(token.getBytes(StandardCharsets.UTF_8)));
        });
    }

    /**
     * Returns the URL of an LSP server, which is chosen based on the
     * current CPU usage
     *
     * @return A URL to the less loaded LSP server
     */
    private String getIntegrationServerUrl() {
        if (this.lspServersStatus.isEmpty()) {
            throw new LspException("Not available");
        }

        this.refreshLspStatus(false);

        List<LspServerStatus> healthyRunningServers = this.lspServersStatus.stream().filter(status -> (status.isHealthy() && !status.isPaused())).collect(Collectors.toList());

        if (healthyRunningServers.size() == 0) {
            this.refreshLspStatus(true);
            healthyRunningServers = this.lspServersStatus.stream().filter(status -> (status.isHealthy() && !status.isPaused())).collect(Collectors.toList());
        }

        if (healthyRunningServers.size() == 0) {
            throw new LspException("Not available");
        }

        double minValue = Integer.MAX_VALUE;
        String serverUrl = healthyRunningServers.get(0).getUrl();

        for (LspServerStatus entry : healthyRunningServers) {
            if (entry.getCpuUsage() < minValue) {
                minValue = entry.getCpuUsage();
                serverUrl = entry.getUrl();
            }
        }

        return serverUrl;
    }

    /**
     * Returns a list of registered external LSP servers
     *
     * @return List of LSP servers' URL
     */
    public List<String> getLspServers() {
        return this.lspServersStatus.stream().map(LspServerStatus::getUrl).toList();
    }

    /**
     * Returns the status of all registered LSP servers
     *
     * @return Map containing the LSP server's URL as Key and status as value
     */
    public List<LspServerStatus> getLspServersStatus(boolean updateMetrics) {
        if (updateMetrics) {
            refreshLspStatus(true);
        }
        return this.lspServersStatus;
    }

    /**
     * Checks the status of the given LSP server URL and adds
     * it if healthy. Otherwise, null is returned.
     *
     * @param serverUrl The URL of the server to add
     * @return The status of the newly added server, or null if unhealthy
     */
    public LspServerStatus addLspServer(String serverUrl) {
        if (this.lspServersStatus.stream().noneMatch(status -> status.getUrl().equals(serverUrl))) {
            LspServerStatus newServer = this.checkLspServerHealth(new LspServerStatus(serverUrl));
            if (newServer.isHealthy()) {
                this.lspServersStatus.add(newServer);
                return newServer;
            }
        }
        throw new LspException("Unable to connecth to the new server");
    }

    /**
     * Pause/Resume a given server by setting its property accordingly
     *
     * @param serverUrl The URL of the server to pause/resume
     * @return The new paused state of the server
     */
    public boolean pauseLspServer(String serverUrl) {
        Optional<LspServerStatus> optionalServer = this.lspServersStatus.stream().filter(status -> status.getUrl().equals(serverUrl)).findFirst();

        if (optionalServer.isPresent()) {
            LspServerStatus serverStatus = optionalServer.get();
            int index = this.lspServersStatus.indexOf(serverStatus);
            serverStatus.setPaused(!serverStatus.isPaused());
            this.lspServersStatus.set(index, serverStatus);
            return serverStatus.isPaused();
        }
        else {
            throw new LspException("Server not found");
        }
    }

    /**
     * Refreshes the status of all registered LSP servers which haven't been checked
     * in the last 5 minutes.
     *
     * @param force Refreshes the LSP status for all servers, including
     *                  unhealthy ones or not reachable ones.
     *
     */
    private void refreshLspStatus(boolean force) {
        for (int i = 0; i < this.lspServersStatus.size(); i++) {
            LspServerStatus status = this.lspServersStatus.get(i);
            if (force || status.getTimestamp() == null || (status.getTimestamp() != null && status.getTimestamp().before(new Date(System.currentTimeMillis() - (5 * 60 * 1000))))
                    || this.lspServersStatus.stream().noneMatch(LspServerStatus::isHealthy)) {
                this.lspServersStatus.set(i, this.checkLspServerHealth(status));
            }
        }
    }

    /**
     * Checks the health of an LSP server and returns its
     * current status
     *
     * @param serverStatus The url to the LSP server to check
     * @return the current server's status
     */
    private LspServerStatus checkLspServerHealth(LspServerStatus serverStatus) {
        HttpGet request = new HttpGet(serverStatus.getUrl() + this.lspHealthEndpoint);
        integrationServerToken.ifPresent((token) -> request.setHeader("authorization", token));

        RequestConfig.Builder requestConfig = RequestConfig.custom();
        requestConfig.setConnectionRequestTimeout(3000);
        requestConfig.setConnectTimeout(3000);
        requestConfig.setSocketTimeout(3000);
        request.setConfig(requestConfig.build());

        try {
            appendApiTokenHeader(request);
            HttpResponse response = this.client.execute(request);

            if (response.getStatusLine().getStatusCode() != 200) {
                EntityUtils.consumeQuietly(response.getEntity());
                throw new LspException("Server responded with code:".concat(String.valueOf(response.getStatusLine().getStatusCode())));
            }
            LspServerStatus serverHealth = new ObjectMapper().readValue(response.getEntity().getContent(), LspServerStatus.class);
            serverHealth.setUrl(serverStatus.getUrl());
            serverHealth.setHealthy(true);
            serverHealth.setPaused(serverStatus.isPaused());
            serverHealth.setTimestamp(new Date());

            EntityUtils.consumeQuietly(response.getEntity());
            return serverHealth;
        }
        catch (LspException | IOException e) {
            log.warn("Error occurred while checking health of {} : {}", serverStatus.getUrl(), e.getMessage());
            LspServerStatus unhealthyStatus = new LspServerStatus(serverStatus.getUrl());
            unhealthyStatus.setHealthy(false);
            unhealthyStatus.setPaused(serverStatus.isPaused());
            unhealthyStatus.setTimestamp(new Date());
            return unhealthyStatus;
        }
    }

    /**
     * Method validating a provided LSP server URL by checking
     * if contained in the list of connected servers. If not,
     * an exception is thrown.
     *
     * @param serverUrl The serverUrl to validate
     */
    private void validateServerUrl(String serverUrl) {
        if (this.lspServersStatus.stream().noneMatch(lspServer -> lspServer.getUrl().equals(serverUrl))) {
            throw new BadRequestException();
        }
    }
}
