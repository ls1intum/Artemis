package de.tum.in.www1.artemis.web.rest;

import javax.ws.rs.BadRequestException;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.PrivacyStatementService;

/**
 * REST controller for managing and retrieving the Privacy Statement.
 */
@RestController
@RequestMapping("api/")
public class PrivacyStatementResource {

    private final PrivacyStatementService privacyStatementService;

    public PrivacyStatementResource(PrivacyStatementService privacyStatementService) {
        this.privacyStatementService = privacyStatementService;
    }

    /**
     * GET /privacy-statement : Returns the privacy statement if you want to view it.
     * not secured as anybody should be able to see the privacy statement (even if not logged in)
     *
     * @param language the language of the privacy statement
     * @return the privacy statement
     */
    @EnforceNothing
    @GetMapping("privacy-statement")
    public PrivacyStatement getPrivacyStatement(@RequestParam("language") String language) {
        if (!PrivacyStatementLanguage.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return privacyStatementService.getPrivacyStatement(PrivacyStatementLanguage.fromLanguageShortName(language));
    }

    /**
     * GET /privacy-statement-for-update : Returns the privacy statement if you want to update it.
     * only accessible for admins
     *
     * @param language the language of the privacy statement
     * @return the privacy statement
     */
    @EnforceAdmin
    @GetMapping("privacy-statement-for-update")
    public PrivacyStatement getPrivacyStatementForUpdate(@RequestParam("language") String language) {
        if (!PrivacyStatementLanguage.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return privacyStatementService.getPrivacyStatementForUpdate(PrivacyStatementLanguage.fromLanguageShortName(language));
    }

    /**
     * PUT /privacy-statement : Updates the privacy statement.
     *
     * @param privacyStatement the privacy statement to update
     * @return the updated privacy statement
     */
    @EnforceAdmin
    @PutMapping("privacy-statement")
    public PrivacyStatement updatePrivacyStatement(@RequestBody PrivacyStatement privacyStatement) {
        return privacyStatementService.updatePrivacyStatement(privacyStatement);
    }
}
