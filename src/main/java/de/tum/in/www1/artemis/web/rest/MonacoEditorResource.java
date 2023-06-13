package de.tum.in.www1.artemis.web.rest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import org.apache.http.conn.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.LspConfig;
import de.tum.in.www1.artemis.domain.LspServerStatus;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.service.MonacoEditorService;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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

    private final FeatureToggleService featureToggleService;

    public MonacoEditorResource(ParticipationAuthorizationCheckService authCheckService, FeatureToggleService featureToggleService, MonacoEditorService monacoEditorService,
            ParticipationRepository participationRepository) {
        this.participationRepository = participationRepository;
        this.monacoEditorService = monacoEditorService;
        this.authCheckService = authCheckService;
        this.featureToggleService = featureToggleService;
    }

    @GetMapping("monaco/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getLspServers() {
        return ResponseEntity.ok(monacoEditorService.getLspServers());
    }

    @GetMapping("monaco/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LspServerStatus>> getLspServersStatus(@RequestParam("update") boolean updateMetrics) {
        return ResponseEntity.ok(monacoEditorService.getLspServersStatus(updateMetrics));
    }

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

    @PutMapping("monaco/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> pauseLspServers(@RequestParam("monacoServerUrl") String monacoServerUrl) {
        return ResponseEntity.ok(monacoEditorService.pauseLspServer(monacoServerUrl));
    }

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

    // This is just a temporary endpoint to retrieve metrics about the users' editor choice
    @PostMapping("monaco/log-editor-choice/{choice}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> logEditorChoice(@PathVariable("choice") int choice) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("log-editor-choice.txt", true));
            writer.write(new Timestamp(new Date().getTime()) + " - " + choice + "\n");
            writer.close();
        }
        catch (IOException e) {
            log.warn("An error occurred while logging the user's editor choice.");
        }
        return ResponseEntity.ok().build();
    }

}
