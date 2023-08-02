package de.tum.in.www1.artemis.service.monaco;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.shaded.json.JSONObject;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.monaco.LspServerStatus;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

@Service
public class LspService {

    private final Logger log = LoggerFactory.getLogger(LspService.class);

    @Value("${artemis.monaco.token:#{null}}")
    private Optional<String> integrationServerToken = Optional.empty();

    private final HttpClient client;

    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();

    private final List<LspServerStatus> lspServersStatus = new ArrayList<>();

    private final String lspInitEndpoint = "/init-lsp";

    private final String terminalInitEndpoint = "/init-terminal";

    private final String forwardUpdatesEndpoint = "/update-files";

    private final String forwardRenameEndpoint = "/rename-file";

    private final String forwardRemovalEndpoint = "/remove-file";

    private final String lspHealthEndpoint = "/health";

    @Autowired
    public LspService(@Value("${artemis.monaco.lsp-servers:#{null}}") List<String> lspServerUrls) {
        this.client = HttpClientBuilder.create().build();
        if (lspServerUrls != null) {
            lspServerUrls.forEach(url -> this.lspServersStatus.add(new LspServerStatus(url)));
        }
        ses.scheduleAtFixedRate(() -> refreshLspStatus(true), 0, 5, TimeUnit.MINUTES);
    }

    public LspService(List<LspServerStatus> lspServerStatus, HttpClient httpClient) {  // used for testing
        this.client = httpClient;
        this.lspServersStatus.addAll(lspServerStatus);
    }

    /**
     * Method that sends a request to a given LSP server, to initialize a participation
     * by cloning its repository locally.
     *
     * @param lspServerUrl  URL pointing to the LSP server to request the initialization
     * @param repositoryUrl URL pointing to the repository to initialize
     */
    public HttpResponse initLsp(String lspServerUrl, String repositoryUrl) throws IOException, LspException {
        JSONObject lspInitRequest = new JSONObject();
        lspInitRequest.put("repoUrl", repositoryUrl);

        return this.sendRequest(new HttpPost(lspServerUrl + this.lspInitEndpoint), lspInitRequest, false, false);
    }

    /**
     * Method used to request the initialization of a dedicated terminal on an external
     * monaco integration server.
     *
     * @param lspServerUrl        URL pointing to the LSP server to request the initialization
     * @param repositoryUrl       URL pointing to the repository to initialize
     * @param programmingExercise Exercise to initialize the terminal for
     * @return The configuration parameters of the initialized terminal
     */
    public HttpResponse initTerminal(String lspServerUrl, String repositoryUrl, ProgrammingExercise programmingExercise) throws IOException {
        JSONObject terminalInitRequest = new JSONObject();
        terminalInitRequest.put("repoUrl", repositoryUrl);
        terminalInitRequest.put("programmingLanguage", programmingExercise.getProgrammingLanguage().name());
        if (programmingExercise.getProjectType() != null) {
            terminalInitRequest.put("projectType", programmingExercise.getProjectType().name());
        }

        return this.sendRequest(new HttpPost(lspServerUrl + this.terminalInitEndpoint), terminalInitRequest, true, false);
    }

    /**
     * Forwards a list of file changes to a given external monaco server.
     *
     * @param lspServerUrl  URL pointing to the LSP server to request the initialization
     * @param repositoryUrl URL pointing to the repository to initialize
     * @param fileUpdates   List of FileSubmissions to forward
     */
    public void forwardFileUpdates(String lspServerUrl, String repositoryUrl, List<FileSubmission> fileUpdates) throws IOException {
        JSONObject filesUpdateRequest = new JSONObject();
        filesUpdateRequest.put("repoUrl", repositoryUrl);
        filesUpdateRequest.put("changes", fileUpdates);

        this.sendRequest(new HttpPut(lspServerUrl + this.forwardUpdatesEndpoint), filesUpdateRequest, false, true);
    }

    /**
     * Forwards a file renaming to a given external monaco server.
     *
     * @param lspServerUrl  URL pointing to the LSP server to request the initialization
     * @param repositoryUrl URL pointing to the repository to initialize
     * @param fileMove      Entity representing the renaming action
     */
    public void forwardFileRename(String lspServerUrl, String repositoryUrl, FileMove fileMove) throws IOException {
        JSONObject fileRenamingRequest = new JSONObject();
        fileRenamingRequest.put("repoUrl", repositoryUrl);
        fileRenamingRequest.put("previous", fileMove.currentFilePath());
        fileRenamingRequest.put("new", fileMove.newFilename());

        this.sendRequest(new HttpPut(lspServerUrl + this.forwardRenameEndpoint), fileRenamingRequest, false, true);
    }

    /**
     * Forwards a file removal to a given external monaco server.
     *
     * @param lspServerUrl  URL pointing to the LSP server to request the initialization
     * @param repositoryUrl URL pointing to the repository to initialize
     * @param filename      Name of the file that has been removed
     */
    public void forwardFileRemoval(String lspServerUrl, String repositoryUrl, String filename) throws IOException {
        JSONObject fileRemovalRequest = new JSONObject();
        fileRemovalRequest.put("repoUrl", repositoryUrl);
        fileRemovalRequest.put("filename", filename);

        this.sendRequest(new HttpPut(lspServerUrl + this.forwardRemovalEndpoint), fileRemovalRequest, false, true);
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

        if (body != null) {
            request.setEntity(new StringEntity(body.toJSONString(), ContentType.create("application/json", "utf-8")));
        }
        appendApiTokenHeader(request);

        HttpResponse response = this.client.execute(request);

        if (consumeEntity) {
            EntityUtils.consumeQuietly(response.getEntity());
        }

        return response;
    }

    /**
     * Appends the 'x-api-token' header to the request with
     * the corresponding base64 encoded token
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
    public String getLspServerUrl() {
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
     * Returns a list of registered external LSP servers URLs
     *
     * @return List of LSP servers URLs
     */
    public List<String> getLspServersUrl() {
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
    public LspServerStatus checkLspServerHealth(LspServerStatus serverStatus) {
        if (serverStatus == null)
            return null;

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
}
