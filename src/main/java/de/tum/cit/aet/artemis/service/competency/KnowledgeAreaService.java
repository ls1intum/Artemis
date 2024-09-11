package de.tum.cit.aet.artemis.service.competency;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency.KnowledgeAreaRequestDTO;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service for managing {@link KnowledgeArea} entities.
 */
@Profile(PROFILE_CORE)
@Service
public class KnowledgeAreaService {

    private final KnowledgeAreaRepository knowledgeAreaRepository;

    public KnowledgeAreaService(KnowledgeAreaRepository knowledgeAreaRepository) {
        this.knowledgeAreaRepository = knowledgeAreaRepository;
    }

    /**
     * Verifies a knowledge area and then saves it to the database
     *
     * @param knowledgeArea the knowledge area to create
     * @return the created knowledge area
     */
    public KnowledgeArea createKnowledgeArea(KnowledgeAreaRequestDTO knowledgeArea) {
        // fetch the parent from the database if it exists
        KnowledgeArea parent = null;
        if (knowledgeArea.parentId() != null) {
            parent = knowledgeAreaRepository.findByIdElseThrow(knowledgeArea.parentId());
        }

        var knowledgeAreaToCreate = new KnowledgeArea(knowledgeArea.title(), knowledgeArea.shortTitle(), knowledgeArea.description());
        knowledgeAreaToCreate.setParent(parent);
        return knowledgeAreaRepository.save(knowledgeAreaToCreate);
    }

    /**
     * Updates an existing knowledge area with the provided data
     *
     * @param knowledgeAreaId the id of the knowledge area to update
     * @param knowledgeArea   the new knowledge area values
     * @return the updated knowledge area
     */
    public KnowledgeArea updateKnowledgeArea(long knowledgeAreaId, KnowledgeAreaRequestDTO knowledgeArea) {
        var existingKnowledgeArea = knowledgeAreaRepository.findByIdElseThrow(knowledgeAreaId);

        existingKnowledgeArea.setTitle(knowledgeArea.title());
        existingKnowledgeArea.setShortTitle(knowledgeArea.shortTitle());
        existingKnowledgeArea.setDescription(knowledgeArea.description());

        if (knowledgeArea.parentId() == null) {
            existingKnowledgeArea.setParent(null);
        }
        else if (existingKnowledgeArea.getParent() == null || !knowledgeArea.parentId().equals(existingKnowledgeArea.getParent().getId())) {
            var newParent = knowledgeAreaRepository.findByIdElseThrow(knowledgeArea.parentId());
            if (knowledgeAreaRepository.isDescendantOf(newParent.getId(), knowledgeAreaId)) {
                throw new BadRequestException("A knowledge area cannot have itself or one of its descendants as parent");
            }
            existingKnowledgeArea.setParent(newParent);
        }

        return knowledgeAreaRepository.save(existingKnowledgeArea);
    }

    /**
     * Deletes an existing knowledge area with the given id or throws an EntityNotFoundException
     *
     * @param knowledgeAreaId the id of the knowledge area to delete
     */
    public void deleteKnowledgeAreaElseThrow(long knowledgeAreaId) throws EntityNotFoundException {
        if (!knowledgeAreaRepository.existsById(knowledgeAreaId)) {
            throw new EntityNotFoundException("KnowledgeArea", knowledgeAreaId);
        }
        knowledgeAreaRepository.deleteById(knowledgeAreaId);
    }
}
