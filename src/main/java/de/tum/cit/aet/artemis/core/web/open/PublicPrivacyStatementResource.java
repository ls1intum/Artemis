package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.admin.dto.PrivacyStatementDTO;
import de.tum.cit.aet.artemis.admin.service.LegalDocumentService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * REST controller for retrieving the Privacy Statement.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.LEGAL_PAGES)
@RestController
@RequestMapping("api/core/public/")
public class PublicPrivacyStatementResource {

    private final LegalDocumentService legalDocumentService;

    public PublicPrivacyStatementResource(LegalDocumentService legalDocumentService) {
        this.legalDocumentService = legalDocumentService;
    }

    /**
     * GET /privacy-statement : Returns the privacy statement if you want to view it.
     * not secured as anybody should be able to see the privacy statement (even if not logged in)
     *
     * @param language the language of the privacy statement
     * @return the ResponseEntity with status 200 (OK) and with body the privacy statement
     */
    @EnforceNothing
    @GetMapping("privacy-statement")
    @Operation(summary = "Get the privacy statement", description = "Available without signing in. If the requested file is missing, returns the document in the other language when available.")
    @ApiResponse(responseCode = "200", description = "The privacy statement", content = @Content(schema = @Schema(implementation = PrivacyStatementDTO.class)))
    @ApiResponse(responseCode = "400", description = "Missing or unsupported language, or neither language has a file", content = @Content)
    @ApiResponse(responseCode = "500", description = "Could not read the privacy statement file", content = @Content)
    public ResponseEntity<PrivacyStatementDTO> getPrivacyStatement(
            @RequestParam("language") @Parameter(description = "Requested language short name", schema = @Schema(allowableValues = { "en", "de" })) String language) {
        if (!Language.isValidShortName(language)) {
            throw new BadRequestException("Language not supported");
        }
        return ResponseEntity.ok(legalDocumentService.getPrivacyStatement(Language.fromLanguageShortName(language)));
    }
}
