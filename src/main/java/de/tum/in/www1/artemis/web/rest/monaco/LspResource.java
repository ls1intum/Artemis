package de.tum.in.www1.artemis.web.rest.monaco;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.monaco.LspServerStatus;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.service.monaco.LspService;

@RestController
@RequestMapping("/api")
public class LspResource {

    private final Logger log = LoggerFactory.getLogger(LspResource.class);

    private final LspService lspService;

    public LspResource(LspService lspService) {
        this.lspService = lspService;
    }

    @GetMapping("lsp/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getLspServers() {
        return ResponseEntity.ok(lspService.getLspServersUrl());
    }

    @GetMapping("lsp/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LspServerStatus>> getLspServersStatus(@RequestParam("update") boolean updateMetrics) {
        return ResponseEntity.ok(lspService.getLspServersStatus(updateMetrics));
    }

    @PostMapping("lsp/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LspServerStatus> addLspServers(@RequestParam("monacoServerUrl") String monacoServerUrl) {
        try {
            return ResponseEntity.ok(lspService.addLspServer(monacoServerUrl));
        }
        catch (LspException e) {
            log.warn("Unable to connect to the new LSP server: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("lsp/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> pauseLspServers(@RequestParam("monacoServerUrl") String monacoServerUrl) {
        return ResponseEntity.ok(lspService.pauseLspServer(monacoServerUrl));
    }
}
