package de.tum.in.www1.artemis.web.rest;

import javax.ws.rs.BadRequestException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Imprint;
import de.tum.in.www1.artemis.domain.LegalDocumentLanguage;
import de.tum.in.www1.artemis.service.LegalDocumentService;

/**
 * REST controller for managing and retrieving the Privacy Statement.
 */
@RestController
@RequestMapping("/api")
public class ImprintResource {

    private final LegalDocumentService legalDocumentService;

    public ImprintResource(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    /**
     * GET /imprint : Returns the imprint if you want to view it.
     * not secured as anybody should be able to see the imprint (even if not logged in)
     *
     * @param language the language of the imprint
     * @return the imprint
     */
    @GetMapping("/imprint")
    public Imprint getImprint(@RequestParam(value = "language") String language) {
        if (!"de".equalsIgnoreCase(language) && !"en".equalsIgnoreCase(language))
            throw new BadRequestException("Language not supported");
        return legalDocumentService.getImprint(LegalDocumentLanguage.fromLanguageShortName(language));
    }

    /**
     * GET /imprint-for-update : Returns the imprint if you want to update it.
     * only accessible for admins
     *
     * @param language the language of the imprint
     * @return the imprint with the given language
     */
    @GetMapping("/imprint-for-update")
    @PreAuthorize("hasRole('ADMIN')")
    public Imprint getImprintForUpdate(@RequestParam("language") String language) {
        if (!"de".equalsIgnoreCase(language) && !"en".equalsIgnoreCase(language))
            throw new BadRequestException("Language not supported");
        return legalDocumentService.getImprintForUpdate(LegalDocumentLanguage.fromLanguageShortName(language));
    }

    /**
     * PUT /imprint : Updates the imprint. If it doesn't exist it will be created
     *
     * @param imprint the imprint to update
     * @return the updated imprint
     */
    @PutMapping("/imprint")
    @PreAuthorize("hasRole('ADMIN')")
    public Imprint updateImprint(@RequestBody Imprint imprint) {
        if (LegalDocumentLanguage.ENGLISH != imprint.getLanguage() && LegalDocumentLanguage.GERMAN != imprint.getLanguage()) {
            throw new BadRequestException("Language not supported");
        }
        return legalDocumentService.updateImprint(imprint);
    }
}
