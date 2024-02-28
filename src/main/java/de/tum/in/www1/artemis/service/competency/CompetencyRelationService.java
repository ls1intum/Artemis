package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.RelationType;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;

/**
 * Service for managing CompetencyRelations.
 */

@Profile(PROFILE_CORE)
@Service
public class CompetencyRelationService {

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyService competencyService;

    private final CompetencyRepository competencyRepository;

    public CompetencyRelationService(CompetencyRelationRepository competencyRelationRepository, CompetencyService competencyService, CompetencyRepository competencyRepository) {
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyService = competencyService;
        this.competencyRepository = competencyRepository;
    }

    /**
     * Gets a relation between two competencies.
     * <p>
     * The relation is not persisted.
     *
     * @param tailCompetency the tail Competency
     * @param headCompetency the head Competency
     * @param relationType   the type of the relation
     * @return the created CompetencyRelation
     */
    public CompetencyRelation getCompetencyRelation(Competency tailCompetency, Competency headCompetency, RelationType relationType) {
        CompetencyRelation competencyRelation = new CompetencyRelation();
        competencyRelation.setTailCompetency(tailCompetency);
        competencyRelation.setHeadCompetency(headCompetency);
        competencyRelation.setType(relationType);
        return competencyRelation;
    }

    /**
     * Creates a relation between two competencies.
     *
     * @param tailCompetency the tail Competency
     * @param headCompetency the head Competency
     * @param type           the type of the relation
     * @param course         the course the relation belongs to
     * @return the persisted CompetencyRelation
     */
    public CompetencyRelation createCompetencyRelation(Competency tailCompetency, Competency headCompetency, String type, Course course) {
        RelationType relationType;
        try {
            relationType = RelationType.valueOf(type);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value for relation type");
        }

        var relation = getCompetencyRelation(tailCompetency, headCompetency, relationType);
        var competencies = competencyRepository.findAllForCourse(course.getId());
        var competencyRelations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        competencyRelations.add(relation);
        if (competencyService.doesCreateCircularRelation(competencies, competencyRelations)) {
            throw new BadRequestException("You can't define circular dependencies between competencies");
        }

        return competencyRelationRepository.save(relation);
    }
}
