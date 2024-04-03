package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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
    public KnowledgeArea createKnowledgeArea(KnowledgeArea knowledgeArea) {
        knowledgeAreaIsValidOrElseThrow(knowledgeArea);
        if (knowledgeArea.getId() != null) {
            throw new BadRequestException("A new knowledge cannot already have an id");
        }

        // fetch the parent from the database if it exists
        KnowledgeArea parent = knowledgeArea.getParent();
        if (parent != null) {
            parent = knowledgeAreaRepository.findByIdElseThrow(parent.getId());
        }

        var knowledgeAreaToCreate = new KnowledgeArea(knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription());
        knowledgeAreaToCreate.setParent(parent);
        return knowledgeAreaRepository.save(knowledgeAreaToCreate);
    }

    /**
     * Updates an existing knowledge area with the provided data
     *
     * @param knowledgeArea the new knowledge area values
     * @return the updated knowledge area
     */
    public KnowledgeArea updateKnowledgeArea(KnowledgeArea knowledgeArea) {
        knowledgeAreaIsValidOrElseThrow(knowledgeArea);
        var existingKnowledgeArea = knowledgeAreaRepository.findByIdElseThrow(knowledgeArea.getId());

        existingKnowledgeArea.setTitle(knowledgeArea.getTitle());
        existingKnowledgeArea.setShortTitle(knowledgeArea.getShortTitle());
        existingKnowledgeArea.setDescription(knowledgeArea.getDescription());

        if (knowledgeArea.getParent() == null) {
            existingKnowledgeArea.setParent(null);
        }
        else if (!knowledgeArea.getParent().equals(existingKnowledgeArea.getParent())) {
            var newParent = knowledgeAreaRepository.findByIdElseThrow(knowledgeArea.getParent().getId());
            if (knowledgeAreaRepository.isDescendantOf(newParent.getId(), knowledgeArea.getId())) {
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

    /**
     * Verifies that a knowledge area that should be created is valid or throws a BadRequestException
     *
     * @param knowledgeArea the knowledge area to verify
     */
    private void knowledgeAreaIsValidOrElseThrow(KnowledgeArea knowledgeArea) throws BadRequestException {
        boolean titleIsInvalid = knowledgeArea.getTitle() == null || knowledgeArea.getTitle().isBlank() || knowledgeArea.getTitle().length() > KnowledgeArea.MAX_TITLE_LENGTH;
        boolean shortTitleIsInvalid = knowledgeArea.getShortTitle() == null || knowledgeArea.getShortTitle().isBlank()
                || knowledgeArea.getShortTitle().length() > KnowledgeArea.MAX_SHORT_TITLE_LENGTH;
        boolean descriptionIsInvalid = knowledgeArea.getDescription() != null && knowledgeArea.getDescription().length() > KnowledgeArea.MAX_DESCRIPTION_LENGTH;

        if (titleIsInvalid) {
            throw new BadRequestException("A knowledge area must have a title and it cannot be longer than " + KnowledgeArea.MAX_TITLE_LENGTH + " characters");
        }
        if (shortTitleIsInvalid) {
            throw new BadRequestException("A knowledge area must have a shortTitle and it cannot be longer than " + KnowledgeArea.MAX_SHORT_TITLE_LENGTH + " characters");
        }
        if (descriptionIsInvalid) {
            throw new BadRequestException("The description of a knowledge area cannot be longer than \" + KnowledgeArea.MAX_SHORT_TITLE_LENGTH + \" characters");
        }
    }

}
