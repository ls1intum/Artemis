package de.tum.cit.aet.artemis.web.rest.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.domain.enumeration.Language;
import de.tum.cit.aet.artemis.service.LegalDocumentService;
import de.tum.cit.aet.artemis.web.rest.dto.PrivacyStatementDTO;

/**
 * REST controller for editing the Privacy Statement as an admin.
 */
@Profile(PROFILE_CORE)
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
     * @return the ResponseEntity with status 200 (OK) and with body the privacy statement
     */
    @EnforceAdmin
    @GetMapping("privacy-statement-for-update")
    public ResponseEntity<PrivacyStatementDTO> getPrivacyStatementForUpdate(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return ResponseEntity.ok(legalDocumentService.getPrivacyStatementForUpdate(Language.fromLanguageShortName(language)));
    }

    /**
     * PUT /privacy-statement : Updates the privacy statement.
     *
     * @param privacyStatement the privacy statement to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated privacy statement
     */
    @EnforceAdmin
    @PutMapping("privacy-statement")
    public ResponseEntity<PrivacyStatementDTO> updatePrivacyStatement(@RequestBody PrivacyStatementDTO privacyStatement) {
        return ResponseEntity.ok(legalDocumentService.updatePrivacyStatement(privacyStatement));
    }
}
