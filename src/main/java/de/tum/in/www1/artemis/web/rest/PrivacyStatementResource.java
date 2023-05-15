package de.tum.in.www1.artemis.web.rest;

import javax.ws.rs.BadRequestException;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.LegalDocumentService;

/**
 * REST controller for managing and retrieving the Privacy Statement.
 */
@RestController
@RequestMapping("api/")
public class PrivacyStatementResource {

    private final LegalDocumentService legalDocumentService;

    public PrivacyStatementResource(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    /**
     * GET /privacy-statement : Returns the privacy statement if you want to view it.
     * not secured as anybody should be able to see the privacy statement (even if not logged in)
     *
     * @param language the language of the privacy statement
     * @return the privacy statement
     */
    @GetMapping("privacy-statement")
    public PrivacyStatement getPrivacyStatement(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return legalDocumentService.getPrivacyStatement(Language.fromLanguageShortName(language));
    }

    /**
     * GET /privacy-statement-for-update : Returns the privacy statement if you want to update it.
     * only accessible for admins
     *
     * @param language the language of the privacy statement
     * @return the privacy statement
     */
    @GetMapping("privacy-statement-for-update")
    @EnforceAdmin
    public PrivacyStatement getPrivacyStatementForUpdate(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return legalDocumentService.getPrivacyStatementForUpdate(Language.fromLanguageShortName(language));
    }

    /**
     * PUT /privacy-statement : Updates the privacy statement.
     *
     * @param privacyStatement the privacy statement to update
     * @return the updated privacy statement
     */
    @PutMapping("privacy-statement")
    @EnforceAdmin
    public PrivacyStatement updatePrivacyStatement(@RequestBody PrivacyStatement privacyStatement) {
        return legalDocumentService.updatePrivacyStatement(privacyStatement);
    }
}
