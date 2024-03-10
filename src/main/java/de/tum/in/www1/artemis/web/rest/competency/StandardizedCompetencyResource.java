package de.tum.in.www1.artemis.web.rest.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;

/**
 * REST controller for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class StandardizedCompetencyResource {

    private final static Logger log = LoggerFactory.getLogger(StandardizedCompetencyResource.class);

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final KnowledgeAreaRepository knowledgeAreaRepository;

    public StandardizedCompetencyResource(StandardizedCompetencyRepository standardizedCompetencyRepository, KnowledgeAreaRepository knowledgeAreaRepository) {
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.knowledgeAreaRepository = knowledgeAreaRepository;
    }

    /**
     * GET api/standardized-competencies/{competencyId} : Gets a standardized competency
     *
     * @param competencyId the id of the standardized competency to get
     * @return the ResponseEntity with status 200 (OK) and with body containing the standardized competency, or with status 404 (Not Found)
     */

    @GetMapping("standardized-competencies/{competencyId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<StandardizedCompetency> getStandardizedCompetency(@PathVariable long competencyId) {
        log.debug("REST request to get standardized competency with id : {}", competencyId);

        var competency = standardizedCompetencyRepository.findByIdElseThrow(competencyId);

        return ResponseEntity.ok().body(competency);
    }

    /**
     * GET api/standardized-competencies/knowledge-areas/{knowledgeAreaId} : Gets a knowledge area with its children and competencies
     *
     * @param knowledgeAreaId the id of the knowledge area to get
     * @return the ResponseEntity with status 200 (OK) and with body containing the knowledge area, or with status 404 (Not Found)
     */
    @GetMapping("standardized-competencies/knowledge-areas/{knowledgeAreaId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<KnowledgeArea> getKnowledgeArea(@PathVariable long knowledgeAreaId) {
        log.debug("REST request to get knowledge area with id : {}", knowledgeAreaId);

        var knowledgeArea = knowledgeAreaRepository.findByIdWithChildrenAndCompetenciesElseThrow(knowledgeAreaId);

        return ResponseEntity.ok().body(knowledgeArea);
    }
}
