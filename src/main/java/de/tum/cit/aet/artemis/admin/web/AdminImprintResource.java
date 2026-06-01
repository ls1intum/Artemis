package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.admin.config.LegacyAdminRestPaths;
import de.tum.cit.aet.artemis.admin.dto.ImprintDTO;
import de.tum.cit.aet.artemis.admin.service.LegalDocumentService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;

/**
 * REST controller for editing the imprint as an admin.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@RestController
@SuppressWarnings("deprecation")
@RequestMapping({ "api/admin/", LegacyAdminRestPaths.CORE_ADMIN_PREFIX })
public class AdminImprintResource {

    private final LegalDocumentService legalDocumentService;

    public AdminImprintResource(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    /**
     * GET /imprint-for-update : Returns the imprint if you want to update it.
     * only accessible for admins
     *
     * @param language the language of the imprint
     * @return the ResponseEntity with status 200 (OK) and with body the imprint with the given language
     */
    @GetMapping("imprint-for-update")
    public ResponseEntity<ImprintDTO> getImprintForUpdate(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return ResponseEntity.ok(legalDocumentService.getImprintForUpdate(Language.fromLanguageShortName(language)));
    }

    /**
     * PUT /imprint : Updates the imprint. If it doesn't exist it will be created
     *
     * @param imprint the imprint to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated imprint
     */
    @PutMapping("imprint")
    public ResponseEntity<ImprintDTO> updateImprint(@RequestBody ImprintDTO imprint) {
        return ResponseEntity.ok(legalDocumentService.updateImprint(imprint));
    }
}
