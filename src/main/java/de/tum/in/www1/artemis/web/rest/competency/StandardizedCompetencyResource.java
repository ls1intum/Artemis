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

import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;

/**
 * REST controller for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class StandardizedCompetencyResource {

    private static final Logger log = LoggerFactory.getLogger(StandardizedCompetencyResource.class);

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    public StandardizedCompetencyResource(StandardizedCompetencyRepository standardizedCompetencyRepository) {
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
    }

    /**
     * GET api/standardized-competencies/{competencyId} : Gets a standardized competency
     *
     * @param competencyId the id of the standardized competency to get
     * @return the ResponseEntity with status 200 (OK) and with body containing the standardized competency, or with status 404 (Not Found)
     */

    @GetMapping("standardized-competencies/{competencyId}")
    @EnforceAdmin
    public ResponseEntity<StandardizedCompetency> getStandardizedCompetency(@PathVariable long competencyId) {
        log.debug("REST request to get Standardized Competency with id : {}", competencyId);

        var competency = standardizedCompetencyRepository.findByIdElseThrow(competencyId);

        return ResponseEntity.ok().body(competency);
    }
}
