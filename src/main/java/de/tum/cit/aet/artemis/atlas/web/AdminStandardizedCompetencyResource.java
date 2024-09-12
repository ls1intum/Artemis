package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.KnowledgeAreaRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.KnowledgeAreaResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.StandardizedCompetencyCatalogDTO;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.StandardizedCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.StandardizedCompetencyResultDTO;
import de.tum.cit.aet.artemis.atlas.service.competency.KnowledgeAreaService;
import de.tum.cit.aet.artemis.atlas.service.competency.StandardizedCompetencyService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * Admin REST controller for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminStandardizedCompetencyResource {

    private static final Logger log = LoggerFactory.getLogger(AdminStandardizedCompetencyResource.class);

    private final StandardizedCompetencyService standardizedCompetencyService;

    private final KnowledgeAreaService knowledgeAreaService;

    public AdminStandardizedCompetencyResource(StandardizedCompetencyService standardizedCompetencyService, KnowledgeAreaService knowledgeAreaService) {
        this.standardizedCompetencyService = standardizedCompetencyService;
        this.knowledgeAreaService = knowledgeAreaService;
    }

    /**
     * POST api/admin/standardized-competencies : Creates a new standardized competency
     *
     * @param competency the standardized competency that should be created
     * @return the ResponseEntity with status 201 (Created) and with body containing the new standardized competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("standardized-competencies")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<StandardizedCompetencyResultDTO> createStandardizedCompetency(@RequestBody @Valid StandardizedCompetencyRequestDTO competency) throws URISyntaxException {
        log.debug("REST request to create standardized competency : {}", competency);

        var persistedCompetency = standardizedCompetencyService.createStandardizedCompetency(competency);

        return ResponseEntity.created(new URI("/api/standardized-competencies/" + persistedCompetency.getId())).body(StandardizedCompetencyResultDTO.of(persistedCompetency));
    }

    /**
     * PUT api/admin/standardized-competencies/{competencyId} : Updates an existing standardized competency
     *
     * @param competencyId the id of the competency that should be updated
     * @param competency   the updated competency
     * @return the ResponseEntity with status 200 (OK) and with body the updated standardized competency
     */
    @PutMapping("standardized-competencies/{competencyId}")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<StandardizedCompetencyResultDTO> updateStandardizedCompetency(@PathVariable long competencyId,
            @RequestBody @Valid StandardizedCompetencyRequestDTO competency) {
        log.debug("REST request to update standardized competency : {}", competency);

        var persistedCompetency = standardizedCompetencyService.updateStandardizedCompetency(competencyId, competency);

        return ResponseEntity.ok(StandardizedCompetencyResultDTO.of(persistedCompetency));
    }

    /**
     * DELETE api/admin/standardized-competencies/{competencyId} : Deletes a standardized competency
     *
     * @param competencyId the id of the competency that should be deleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("standardized-competencies/{competencyId}")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<Void> deleteStandardizedCompetency(@PathVariable long competencyId) {
        log.debug("REST request to delete standardized competency : {}", competencyId);

        standardizedCompetencyService.deleteStandardizedCompetencyElseThrow(competencyId);

        return ResponseEntity.ok().build();
    }

    /**
     * POST api/admin/standardized-competencies/knowledge-areas : Creates a new knowledge area
     *
     * @param knowledgeArea the knowledge area that should be created
     * @return the ResponseEntity with status 201 (Created) and with body containing the new knowledge area
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("standardized-competencies/knowledge-areas")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<KnowledgeAreaResultDTO> createKnowledgeArea(@RequestBody @Valid KnowledgeAreaRequestDTO knowledgeArea) throws URISyntaxException {
        log.debug("REST request to create knowledge area : {}", knowledgeArea);

        var persistedKnowledgeArea = knowledgeAreaService.createKnowledgeArea(knowledgeArea);

        return ResponseEntity.created(new URI("/api/standardized-competencies/knowledge-areas" + persistedKnowledgeArea.getId()))
                .body(KnowledgeAreaResultDTO.of(persistedKnowledgeArea));
    }

    /**
     * PUT api/admin/standardized-competencies/knowledge-areas/{knowledgeAreaId} : Updates an existing knowledge area
     *
     * @param knowledgeAreaId the id of the knowledge area that should be updated
     * @param knowledgeArea   the updated knowledge area
     * @return the ResponseEntity with status 200 (OK) and with body the updated knowledge area
     */
    @PutMapping("standardized-competencies/knowledge-areas/{knowledgeAreaId}")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<KnowledgeAreaResultDTO> updateKnowledgeArea(@PathVariable long knowledgeAreaId, @RequestBody @Valid KnowledgeAreaRequestDTO knowledgeArea) {
        log.debug("REST request to update knowledge area : {}", knowledgeArea);

        var persistedKnowledgeArea = knowledgeAreaService.updateKnowledgeArea(knowledgeAreaId, knowledgeArea);

        return ResponseEntity.ok(KnowledgeAreaResultDTO.of(persistedKnowledgeArea));
    }

    /**
     * DELETE api/admin/standardized-competencies/knowledge-areas/{knowledgeAreaId} : Deletes a knowledge area
     *
     * @param knowledgeAreaId the id of the knowledge area that should be deleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("standardized-competencies/knowledge-areas/{knowledgeAreaId}")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<Void> deleteKnowledgeArea(@PathVariable long knowledgeAreaId) {
        log.debug("REST request to delete knowledge area : {}", knowledgeAreaId);

        knowledgeAreaService.deleteKnowledgeAreaElseThrow(knowledgeAreaId);

        return ResponseEntity.ok().build();
    }

    /**
     * PUT api/admin/standardized-competencies/import : Imports a catalog of standardized competencies, knowledge areas and sources
     *
     * @param standardizedCompetencyCatalogDTO the DTO containing the standardized competency catalog
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("standardized-competencies/import")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<Void> importStandardizedCompetencyCatalog(@RequestBody @Valid StandardizedCompetencyCatalogDTO standardizedCompetencyCatalogDTO) {
        log.debug("REST request to import standardized competency catalog");

        standardizedCompetencyService.importStandardizedCompetencyCatalog(standardizedCompetencyCatalogDTO);

        return ResponseEntity.ok().build();
    }

    /**
     * GET api/admin/standardized-competencies/export : Exports the catalog of standardized competencies, knowledge areas and sources of this Artemis instance
     *
     * @return the ResponseEntity with status 200 (OK) and the body containing the JSON string of the standardized competency catalog
     */
    @GetMapping("standardized-competencies/export")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAdmin
    public ResponseEntity<String> exportStandardizedCompetencyCatalog() {
        log.debug("REST request to export standardized competency catalog");

        String catalog = standardizedCompetencyService.exportStandardizedCompetencyCatalog();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        responseHeaders.setContentDispositionFormData("attachment", "competencies.json");

        return ResponseEntity.ok().headers(responseHeaders).body(catalog);
    }
}
