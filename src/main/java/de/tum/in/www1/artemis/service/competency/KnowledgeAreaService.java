package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;

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

    public KnowledgeArea createKnowledgeArea(KnowledgeArea knowledgeArea) {
        KnowledgeAreaIsValidOrElseThrow(knowledgeArea);

        // fetch the parent from the database if it exists
        KnowledgeArea parent = knowledgeArea.getParent();
        if (parent != null) {
            parent = knowledgeAreaRepository.findByIdElseThrow(parent.getId());
        }

        var knowledgeAreaToCreate = new KnowledgeArea(knowledgeArea.getTitle(), knowledgeArea.getDescription(), parent);

        return knowledgeAreaRepository.save(knowledgeAreaToCreate);
    }

    /**
     * Verifies that a knowledge area that should be created is valid or throws a BadRequestException
     *
     * @param knowledgeArea the knowledge area to verify
     */
    public void KnowledgeAreaIsValidOrElseThrow(KnowledgeArea knowledgeArea) throws BadRequestException {
        if (knowledgeArea.getId() != null || knowledgeArea.getTitle() == null || knowledgeArea.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
    }
}
