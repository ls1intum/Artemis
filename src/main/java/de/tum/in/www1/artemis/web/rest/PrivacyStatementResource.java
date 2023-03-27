package de.tum.in.www1.artemis.web.rest;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.service.PrivacyStatementService;

/**
 * REST controller for managing and retrieving the Privacy Statement.
 */
@RestController
@RequestMapping("/api")
public class PrivacyStatementResource {

    private final PrivacyStatementService privacyStatementService;

    public PrivacyStatementResource(PrivacyStatementService privacyStatementService) {
        this.privacyStatementService = privacyStatementService;
    }

    @GetMapping("/privacy-statement")
    public PrivacyStatement getPrivacyStatement() {
        return privacyStatementService.getPrivacyStatement();
    }

    @PostMapping("/privacy-statement")
    public PrivacyStatement createPrivacyStatement(@RequestBody PrivacyStatement privacyStatement) {
        return privacyStatementService.createPrivacyStatement(privacyStatement);
    }

    @PutMapping("/privacy-statement")
    public PrivacyStatement updatePrivacyStatement(@RequestBody PrivacyStatement privacyStatement) {
        return privacyStatementService.updatePrivacyStatement(privacyStatement);
    }
}
