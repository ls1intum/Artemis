package de.tum.cit.aet.artemis.atlas.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.KnowledgeAreaResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency.SourceDTO;
import de.tum.cit.aet.artemis.atlas.repository.SourceRepository;
import de.tum.cit.aet.artemis.atlas.repository.StandardizedCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.StandardizedCompetencyService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * REST controller for managing {@link StandardizedCompetency} entities.
 */
@Conditional(AtlasEnabled.class)
@FeatureToggle(Feature.StandardizedCompetencies)
@Lazy
@RestController
@RequestMapping("api/atlas/standardized-competencies/")
public class StandardizedCompetencyResource {

    private static final Logger log = LoggerFactory.getLogger(StandardizedCompetencyResource.class);

    private final StandardizedCompetencyService standardizedCompetencyService;

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final SourceRepository sourceRepository;

    public StandardizedCompetencyResource(StandardizedCompetencyService standardizedCompetencyService, StandardizedCompetencyRepository standardizedCompetencyRepository,
            SourceRepository sourceRepository) {
        this.standardizedCompetencyService = standardizedCompetencyService;
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * GET api/standardized-competencies/{competencyId} : Gets a standardized competency
     *
     * @param competencyId the id of the standardized competency to get
     * @return the ResponseEntity with status 200 (OK) and with body containing the standardized competency, or with status 404 (Not Found)
     */
    @GetMapping("{competencyId}")
    @EnforceAtLeastEditor
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
    @EnforceAtLeastEditor
    public ResponseEntity<List<KnowledgeAreaResultDTO>> getAllForTreeView() {
        log.debug("REST request to all knowledge areas for tree view");

        var knowledgeAreas = standardizedCompetencyService.getAllForTreeView();

        return ResponseEntity.ok().body(knowledgeAreas.stream().map(KnowledgeAreaResultDTO::of).toList());
    }

    /**
     * GET api/standardized-competencies/sources : Gets all sources
     *
     * @return the ResponseEntity with status 200 (OK) and with body containing the list of sources
     */
    @GetMapping("sources")
    @EnforceAtLeastEditor
    public ResponseEntity<List<SourceDTO>> getSources() {
        log.debug("REST request to get all sources");

        var sourceDTOs = sourceRepository.findAll().stream().map(SourceDTO::of).toList();

        return ResponseEntity.ok().body(sourceDTOs);
    }
}
