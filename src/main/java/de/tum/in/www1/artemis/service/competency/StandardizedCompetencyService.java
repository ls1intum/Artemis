package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.SourceRepository;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.KnowledgeAreaDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.StandardizedCompetencyDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@Service
public class StandardizedCompetencyService {

    private static final String FIRST_VERSION = "1.0.0";

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final KnowledgeAreaRepository knowledgeAreaRepository;

    private final SourceRepository sourceRepository;

    public StandardizedCompetencyService(StandardizedCompetencyRepository standardizedCompetencyRepository, KnowledgeAreaRepository knowledgeAreaRepository,
            SourceRepository sourceRepository) {
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.knowledgeAreaRepository = knowledgeAreaRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * Verifies a standardized competency and then saves it to the database
     *
     * @param competency the standardized competency to create
     * @return the created standardized competency
     */
    public StandardizedCompetency createStandardizedCompetency(StandardizedCompetencyDTO competency) {

        KnowledgeArea knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.knowledgeAreaId());
        Source source = null;
        if (competency.sourceId() != null) {
            source = sourceRepository.findByIdElseThrow(competency.sourceId());
        }

        var competencyToCreate = new StandardizedCompetency(competency.title().trim(), competency.description(), competency.taxonomy(), FIRST_VERSION);
        competencyToCreate.setKnowledgeArea(knowledgeArea);
        competencyToCreate.setSource(source);

        competencyToCreate = standardizedCompetencyRepository.save(competencyToCreate);
        // set the reference to the first version of this standardized competency (itself)
        competencyToCreate.setFirstVersion(competencyToCreate);
        return standardizedCompetencyRepository.save(competencyToCreate);
    }

    /**
     * Updates an existing standardized competency with the provided competency data
     *
     * @param competency   competency object containing the data to update
     * @param competencyId the id of the competency to update
     * @return the updated standardized competency
     */
    public StandardizedCompetency updateStandardizedCompetency(StandardizedCompetencyDTO competency, long competencyId) {
        var existingCompetency = standardizedCompetencyRepository.findByIdElseThrow(competencyId);

        if (competency.version() != null && !competency.version().equals(existingCompetency.getVersion())) {
            throw new BadRequestException("You cannot change the version of a standardized competency");
        }

        existingCompetency.setTitle(competency.title());
        existingCompetency.setDescription(competency.description());
        existingCompetency.setTaxonomy(competency.taxonomy());

        if (competency.sourceId() == null) {
            existingCompetency.setSource(null);
        }
        else if (existingCompetency.getSource() == null || !competency.sourceId().equals(existingCompetency.getSource().getId())) {
            var source = sourceRepository.findByIdElseThrow(competency.sourceId());
            existingCompetency.setSource(source);
        }

        if (existingCompetency.getKnowledgeArea().getId() != competency.knowledgeAreaId()) {
            var knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.knowledgeAreaId());
            existingCompetency.setKnowledgeArea(knowledgeArea);
        }

        return standardizedCompetencyRepository.save(existingCompetency);
    }

    /**
     * Deletes an existing standardized competency with the given id or throws an EntityNotFoundException
     *
     * @param competencyId the id of the competency to delete
     */
    public void deleteStandardizedCompetencyElseThrow(long competencyId) throws EntityNotFoundException {
        if (!standardizedCompetencyRepository.existsById(competencyId)) {
            throw new EntityNotFoundException("StandardizedCompetency", competencyId);
        }
        standardizedCompetencyRepository.deleteById(competencyId);
    }

    /**
     * Gets a hierarchical structure of all knowledge areas including their competencies, sorted by their title
     *
     * @return the list of knowledge areas with no parent, containing all their descendants and competencies
     */
    public List<KnowledgeAreaDTO> getAllForTreeView() {
        var knowledgeAreasForTreeView = new ArrayList<KnowledgeArea>();
        var idMap = new HashMap<Long, KnowledgeArea>();

        var knowledgeAreas = knowledgeAreaRepository.findAllWithCompetenciesByOrderByTitleAsc();
        for (var knowledgeArea : knowledgeAreas) {
            // use a linked hash set to retain order
            knowledgeArea.setChildren(new LinkedHashSet<>());
            idMap.put(knowledgeArea.getId(), knowledgeArea);
        }

        for (var knowledgeArea : knowledgeAreas) {
            // if the knowledge area has no parent add it to the result list
            if (knowledgeArea.getParent() == null) {
                knowledgeAreasForTreeView.add(knowledgeArea);
                continue;
            }
            // otherwise add it to the children of its parents
            var parent = idMap.get(knowledgeArea.getParent().getId());
            if (parent == null) {
                continue;
            }
            parent.addToChildren(knowledgeArea);
        }

        return knowledgeAreasForTreeView.stream().map(KnowledgeAreaDTO::of).toList();
    }
}
