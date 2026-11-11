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

import de.tum.cit.aet.artemis.admin.dto.ImprintDTO;
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
 * REST controller for editing the imprint as an admin.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage(UserFeature.LEGAL_PAGES)
@RestController
@SuppressWarnings("deprecation")
@RequestMapping("api/admin/")
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
    @Operation(summary = "Get the imprint for editing", description = "Requires an administrator. If the selected language has no file, the response omits text.")
    @ApiResponse(responseCode = "200", description = "The imprint in the requested language", content = @Content(schema = @Schema(implementation = ImprintDTO.class)))
    @ApiResponse(responseCode = "400", description = "Missing or unsupported language", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content)
    @ApiResponse(responseCode = "500", description = "Could not read the imprint file", content = @Content)
    public ResponseEntity<ImprintDTO> getImprintForUpdate(
            @RequestParam("language") @Parameter(description = "Requested language short name", schema = @Schema(allowableValues = { "en", "de" })) String language) {
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
    @Operation(summary = "Save the imprint", description = "Requires an administrator. Creates the file if it does not exist.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Provide type, language, and nonblank Markdown text.", content = @Content(schema = @Schema(allOf = ImprintDTO.class, requiredProperties = "text"))))
    @ApiResponse(responseCode = "200", description = "The saved imprint", content = @Content(schema = @Schema(implementation = ImprintDTO.class)))
    @ApiResponse(responseCode = "400", description = "Blank Markdown text", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Administrator role required", content = @Content)
    @ApiResponse(responseCode = "500", description = "Could not write the imprint file", content = @Content)
    public ResponseEntity<ImprintDTO> updateImprint(@RequestBody ImprintDTO imprint) {
        return ResponseEntity.ok(legalDocumentService.updateImprint(imprint));
    }
}
