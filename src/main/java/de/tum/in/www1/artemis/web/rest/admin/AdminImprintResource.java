package de.tum.in.www1.artemis.web.rest.admin;

import javax.ws.rs.BadRequestException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Imprint;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.LegalDocumentService;

/**
 * REST controller for editing the imprint as an admin.
 */
@RestController
@RequestMapping("api/admin")
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
     * @return the imprint with the given language
     */
    @GetMapping("imprint-for-update")
    @EnforceAdmin
    public Imprint getImprintForUpdate(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return legalDocumentService.getImprintForUpdate(Language.fromLanguageShortName(language));
    }

    /**
     * PUT /imprint : Updates the imprint. If it doesn't exist it will be created
     *
     * @param imprint the imprint to update
     * @return the updated imprint
     */
    @PutMapping("imprint")
    @EnforceAdmin
    public Imprint updateImprint(@RequestBody Imprint imprint) {
        return legalDocumentService.updateImprint(imprint);
    }
}
