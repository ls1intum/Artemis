package de.tum.in.www1.artemis.web.rest.open;

import javax.ws.rs.BadRequestException;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Imprint;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.LegalDocumentService;

/**
 * REST controller for retrieving the imprint.
 */
@RestController
@RequestMapping("api/public/")
public class PublicImprintResource {

    private final LegalDocumentService legalDocumentService;

    public PublicImprintResource(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    /**
     * GET /imprint : Returns the imprint if you want to view it.
     * not secured as anybody should be able to see the imprint (even if not logged in)
     *
     * @param language the language of the imprint
     * @return the imprint
     */
    @GetMapping("imprint")
    @EnforceNothing
    public Imprint getImprint(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return legalDocumentService.getImprint(Language.fromLanguageShortName(language));
    }
}
