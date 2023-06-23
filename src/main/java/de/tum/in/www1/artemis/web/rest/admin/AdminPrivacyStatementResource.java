package de.tum.in.www1.artemis.web.rest.admin;

import javax.ws.rs.BadRequestException;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.LegalDocumentService;

/**
 * REST controller for editing the Privacy Statement as an admin.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminPrivacyStatementResource {

    private final LegalDocumentService legalDocumentService;

    public AdminPrivacyStatementResource(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
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
    @EnforceAdmin
    @PutMapping("privacy-statement")
    public PrivacyStatement updatePrivacyStatement(@RequestBody PrivacyStatement privacyStatement) {
        return legalDocumentService.updatePrivacyStatement(privacyStatement);
    }
}
