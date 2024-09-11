package de.tum.cit.aet.artemis.web.rest.open;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.enumeration.Language;
import de.tum.cit.aet.artemis.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.service.LegalDocumentService;
import de.tum.cit.aet.artemis.web.rest.dto.ImprintDTO;

/**
 * REST controller for retrieving the imprint.
 */
@Profile(PROFILE_CORE)
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
     * @return the ResponseEntity with status 200 (OK) and with body the imprint
     */
    @GetMapping("imprint")
    @EnforceNothing
    public ResponseEntity<ImprintDTO> getImprint(@RequestParam("language") String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return ResponseEntity.ok(legalDocumentService.getImprint(Language.fromLanguageShortName(language)));
    }
}
