package de.tum.in.www1.artemis.web.rest.open;

import javax.ws.rs.BadRequestException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.PrivacyStatementService;

/**
 * REST controller for retrieving the Privacy Statement.
 */
@RestController
@RequestMapping("api/public/")
public class PublicPrivacyStatementResource {

    private final PrivacyStatementService privacyStatementService;

    public PublicPrivacyStatementResource(PrivacyStatementService privacyStatementService) {
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
}
