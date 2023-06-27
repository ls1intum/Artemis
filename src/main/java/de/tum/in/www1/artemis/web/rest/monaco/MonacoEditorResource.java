package de.tum.in.www1.artemis.web.rest.monaco;

import java.io.IOException;
import java.util.List;

import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.monaco.LspConfig;
import de.tum.in.www1.artemis.domain.monaco.LspServerStatus;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.monaco.MonacoEditorService;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing LSP and Monaco related endpoints
 */

@Profile("lsp")
@RestController
@RequestMapping("/api")
public class MonacoEditorResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "monacoEditor";

    private final Logger log = LoggerFactory.getLogger(MonacoEditorResource.class);

    private final ParticipationRepository participationRepository;

    private final MonacoEditorService monacoEditorService;

    private final ParticipationAuthorizationCheckService authCheckService;

    public MonacoEditorResource(ParticipationAuthorizationCheckService authCheckService, MonacoEditorService monacoEditorService, ParticipationRepository participationRepository) {
        this.participationRepository = participationRepository;
        this.monacoEditorService = monacoEditorService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /monaco/list : Retrieve list of LSP servers
     *
     * @return A list of connected LSP servers
     */
    @GetMapping("monaco/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getLspServers() {
        return ResponseEntity.ok(monacoEditorService.getLspServers());
    }

    /**
     * GET /monaco/status : Retrieval of statuses of connected LSP server
     *
     * @param updateMetrics If true, requests the status metrics to be updated before retrieval
     * @return List of status metrics of each connected LSP server
     */
    @GetMapping("monaco/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LspServerStatus>> getLspServersStatus(@RequestParam("update") boolean updateMetrics) {
        return ResponseEntity.ok(monacoEditorService.getLspServersStatus(updateMetrics));
    }

    /**
     * POST /monaco/add : Adds a new LSP server to connect to
     *
     * @param monacoServerUrl URL pointing to the server to connect to
     * @return The status metrics of the added server
     */
    @PostMapping("monaco/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LspServerStatus> addLspServers(@RequestParam("monacoServerUrl") String monacoServerUrl) {
        try {
            return ResponseEntity.ok(monacoEditorService.addLspServer(monacoServerUrl));
        }
        catch (LspException e) {
            log.warn("Unable to connect to the new LSP server: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * PUT /monaco/pause : Requests the pausing of a given LSP server which won't be
     * assigned any more user sessions until resumed.
     *
     * @param monacoServerUrl URL pointing to the server to pause
     * @return A boolean value representing the paused state of the LSP server
     */
    @PutMapping("monaco/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> pauseLspServers(@RequestParam("monacoServerUrl") String monacoServerUrl) {
        return ResponseEntity.ok(monacoEditorService.pauseLspServer(monacoServerUrl));
    }

    /**
     * GET /monaco/init-lsp/:participationId : Requests the initialization of a new LSP session
     * related to a given participation
     *
     * @param participationId The ID of the participation related to the LSP session to initialize
     * @return The configuration parameters of the initialized LSP participation
     */
    @GetMapping("monaco/init-lsp/{participationId}")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.LSP)
    public ResponseEntity<LspConfig> initLsp(@PathVariable("participationId") Long participationId) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!authCheckService.canAccessParticipation(participation)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            return ResponseEntity.ok(monacoEditorService.initLsp(participation));
        }
        catch (HttpHostConnectException hhce) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        catch (LspException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "serviceUnavailable", e.getMessage())).body(null);
        }
        catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * GET /monaco/init-terminal/:participationId : Requests the initialization of a new terminal session
     * related to a given participation
     *
     * @param participationId The ID of the participation related to the terminal session to initialize
     * @param monacoServerUrl URL pointing to the LSP server managing the related LSP session
     * @return The configuration parameters of the initialized LSP server terminal session
     */
    @GetMapping("monaco/init-terminal/{participationId}")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.EditorTerminal)
    public ResponseEntity<LspConfig> initTerminal(@PathVariable("participationId") Long participationId, @RequestParam("monacoServerUrl") String monacoServerUrl) {

        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!authCheckService.canAccessParticipation(participation)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            return ResponseEntity.ok(monacoEditorService.initTerminal(participation, monacoServerUrl));
        }
        catch (HttpHostConnectException hhce) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * PUT /monaco/update-files/:participationId : Forwards files changes to the LSP server
     * managing the LSP session.
     *
     * @param fileUpdates     List of files changes to forward
     * @param participationId The ID of the participation related to the LSP session
     * @param monacoServerUrl URL pointing to the LSP server managing the LSP session
     * @return HTTP 200 acknowledging the correct updates forwarding
     */
    @PutMapping("monaco/update-files/{participationId}")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.LSP)
    public ResponseEntity<Void> updateFiles(@RequestBody List<FileSubmission> fileUpdates, @PathVariable("participationId") Long participationId,
            @RequestParam("monacoServerUrl") String monacoServerUrl) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!authCheckService.canAccessParticipation(participation)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        monacoEditorService.forwardFileUpdates(participation, fileUpdates, monacoServerUrl);
        return ResponseEntity.ok().build();
    }
}
