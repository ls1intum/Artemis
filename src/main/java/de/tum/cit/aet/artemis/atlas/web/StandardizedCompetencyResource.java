package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.atlas.repository.SourceRepository;
import de.tum.cit.aet.artemis.atlas.repository.competency.KnowledgeAreaRepository;
import de.tum.cit.aet.artemis.atlas.repository.competency.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.StandardizedCompetencyService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.KnowledgeAreaResultDTO;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.SourceDTO;

/**
 * REST controller for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/standardized-competencies/")
public class StandardizedCompetencyResource {

    private static final Logger log = LoggerFactory.getLogger(StandardizedCompetencyResource.class);

    private final StandardizedCompetencyService standardizedCompetencyService;

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final KnowledgeAreaRepository knowledgeAreaRepository;

    private final SourceRepository sourceRepository;

    public StandardizedCompetencyResource(StandardizedCompetencyService standardizedCompetencyService, StandardizedCompetencyRepository standardizedCompetencyRepository,
            KnowledgeAreaRepository knowledgeAreaRepository, SourceRepository sourceRepository) {
        this.standardizedCompetencyService = standardizedCompetencyService;
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.knowledgeAreaRepository = knowledgeAreaRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * GET api/standardized-competencies/{competencyId} : Gets a standardized competency
     *
     * @param competencyId the id of the standardized competency to get
     * @return the ResponseEntity with status 200 (OK) and with body containing the standardized competency, or with status 404 (Not Found)
     */
    @GetMapping("{competencyId}")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAtLeastInstructor
    public ResponseEntity<StandardizedCompetency> getStandardizedCompetency(@PathVariable long competencyId) {
        log.debug("REST request to get standardized competency with id : {}", competencyId);

        var competency = standardizedCompetencyRepository.findByIdElseThrow(competencyId);

        return ResponseEntity.ok(competency);
    }

    /**
     * GET api/standardized-competencies/for-tree-view : Gets a hierarchical structure of all knowledge areas including their competencies
     *
     * @return the ResponseEntity with status 200 (OK) and with body containing the knowledge areas
     */
    @GetMapping("for-tree-view")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAtLeastInstructor
    public ResponseEntity<List<KnowledgeAreaResultDTO>> getAllForTreeView() {
        log.debug("REST request to all knowledge areas for tree view");

        var knowledgeAreas = standardizedCompetencyService.getAllForTreeView();

        return ResponseEntity.ok().body(knowledgeAreas.stream().map(KnowledgeAreaResultDTO::of).toList());
    }

    /**
     * GET api/standardized-competencies/knowledge-areas/{knowledgeAreaId} : Gets a knowledge area with its children and competencies
     *
     * @param knowledgeAreaId the id of the knowledge area to get
     * @return the ResponseEntity with status 200 (OK) and with body containing the knowledge area, or with status 404 (Not Found)
     */
    @GetMapping("knowledge-areas/{knowledgeAreaId}")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAtLeastInstructor
    public ResponseEntity<KnowledgeArea> getKnowledgeArea(@PathVariable long knowledgeAreaId) {
        log.debug("REST request to get knowledge area with id : {}", knowledgeAreaId);

        var knowledgeArea = knowledgeAreaRepository.findWithChildrenAndCompetenciesByIdElseThrow(knowledgeAreaId);

        return ResponseEntity.ok().body(knowledgeArea);
    }

    /**
     * GET api/standardized-competencies/sources : Gets all sources
     *
     * @return the ResponseEntity with status 200 (OK) and with body containing the list of sources
     */
    @GetMapping("sources")
    @FeatureToggle(Feature.StandardizedCompetencies)
    @EnforceAtLeastInstructor
    public ResponseEntity<List<SourceDTO>> getSources() {
        log.debug("REST request to get all sources");

        var sourceDTOs = sourceRepository.findAll().stream().map(SourceDTO::of).toList();

        return ResponseEntity.ok().body(sourceDTOs);
    }
}
