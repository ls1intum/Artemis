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

import de.tum.cit.aet.artemis.admin.dto.PrivacyStatementDTO;
import de.tum.cit.aet.artemis.admin.service.LegalDocumentService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * REST controller for editing the Privacy Statement as an admin.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage(UserFeature.LEGAL_PAGES)
@RestController
@SuppressWarnings("deprecation")
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
    @GetMapping("privacy-statement-for-update")
    @Operation(summary = "Get the privacy statement for editing", description = "Requires an administrator. If the selected language has no file, the response omits text.")
    @ApiResponse(responseCode = "200", description = "The privacy statement in the requested language", content = @Content(schema = @Schema(implementation = PrivacyStatementDTO.class)))
    @ApiResponse(responseCode = "400", description = "Missing or unsupported language", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content)
    @ApiResponse(responseCode = "500", description = "Could not read the privacy statement file", content = @Content)
    public ResponseEntity<PrivacyStatementDTO> getPrivacyStatementForUpdate(
            @RequestParam("language") @Parameter(description = "Requested language short name", schema = @Schema(allowableValues = { "en", "de" })) String language) {
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
    @PutMapping("privacy-statement")
    @Operation(summary = "Save the privacy statement", description = "Requires an administrator. Creates the file if it does not exist.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Provide type, language, and nonblank Markdown text.", content = @Content(schema = @Schema(allOf = PrivacyStatementDTO.class, requiredProperties = "text"))))
    @ApiResponse(responseCode = "200", description = "The saved privacy statement", content = @Content(schema = @Schema(implementation = PrivacyStatementDTO.class)))
    @ApiResponse(responseCode = "400", description = "Blank Markdown text", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content)
    @ApiResponse(responseCode = "500", description = "Could not write the privacy statement file", content = @Content)
    public ResponseEntity<PrivacyStatementDTO> updatePrivacyStatement(@RequestBody PrivacyStatementDTO privacyStatement) {
        return ResponseEntity.ok(legalDocumentService.updatePrivacyStatement(privacyStatement));
    }
}
