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
    public StandardizedCompetency createStandardizedCompetency(StandardizedCompetency competency) {
        standardizedCompetencyIsValidOrElseThrow(competency);

        // fetch the knowledge area and source from the database if they exist
        KnowledgeArea knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.getKnowledgeArea().getId());
        Source source = competency.getSource();
        if (source != null) {
            source = sourceRepository.findByIdElseThrow(source.getId());
        }

        var competencyToCreate = new StandardizedCompetency(competency.getTitle().trim(), competency.getDescription(), competency.getTaxonomy(), FIRST_VERSION);
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
     * @param competency competency object containing the data to update
     * @return the updated standardized competency
     */
    public StandardizedCompetency updateStandardizedCompetency(StandardizedCompetency competency) {
        standardizedCompetencyIsValidOrElseThrow(competency);
        var existingCompetency = standardizedCompetencyRepository.findByIdElseThrow(competency.getId());

        if (competency.getVersion() != null && !competency.getVersion().equals(existingCompetency.getVersion())) {
            throw new BadRequestException("You cannot change the version of a standardized competency");
        }
        if (competency.getFirstVersion() != null && !competency.getFirstVersion().equals(existingCompetency.getFirstVersion())) {
            throw new BadRequestException("You cannot change the first version of a standardized competency");
        }

        existingCompetency.setTitle(competency.getTitle());
        existingCompetency.setDescription(competency.getDescription());
        existingCompetency.setTaxonomy(competency.getTaxonomy());
        if (competency.getSource() == null) {
            existingCompetency.setSource(null);
        }
        else if (!competency.getSource().equals(existingCompetency.getSource())) {
            var source = sourceRepository.findByIdElseThrow(competency.getSource().getId());
            existingCompetency.setSource(source);
        }
        if (!competency.getKnowledgeArea().equals(existingCompetency.getKnowledgeArea())) {
            var knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.getKnowledgeArea().getId());
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

    /**
     * Verifies that a standardized competency that is valid or throws a BadRequestException
     *
     * @param competency the standardized competency to verify
     */
    private void standardizedCompetencyIsValidOrElseThrow(StandardizedCompetency competency) throws BadRequestException {
        boolean titleIsInvalid = competency.getTitle() == null || competency.getTitle().trim().isEmpty()
                || competency.getTitle().length() > StandardizedCompetency.MAX_TITLE_LENGTH;
        boolean descriptionIsInvalid = competency.getDescription() != null && competency.getDescription().length() > StandardizedCompetency.MAX_DESCRIPTION_LENGTH;
        boolean knowledgeAreaIsInvalid = competency.getKnowledgeArea() == null || competency.getKnowledgeArea().getId() == null;

        if (titleIsInvalid) {
            throw new BadRequestException("A standardized competency must have a title and it cannot be longer than " + StandardizedCompetency.MAX_TITLE_LENGTH + " characters");
        }
        if (descriptionIsInvalid) {
            throw new BadRequestException("The description of a standardized competency cannot be longer than " + StandardizedCompetency.MAX_DESCRIPTION_LENGTH + " characters");
        }
        if (knowledgeAreaIsInvalid) {
            throw new BadRequestException("A standardized competency must be part of a knowledge area whose id is not null");
        }
    }
}
