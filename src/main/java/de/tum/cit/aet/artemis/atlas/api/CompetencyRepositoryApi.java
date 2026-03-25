package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyRepositoryApi extends AbstractAtlasApi {

    private final CompetencyRepository competencyRepository;

    private final PrerequisiteRepository prerequisiteRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CompetencyRepositoryApi(CompetencyRepository competencyRepository, PrerequisiteRepository prerequisiteRepository,
            CourseCompetencyRepository courseCompetencyRepository) {
        this.competencyRepository = competencyRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    public Set<Competency> findAllCompetenciesByCourseId(long courseId) {
        return competencyRepository.findAllByCourseId(courseId);
    }

    /**
     * Finds a CourseCompetency (Competency or Prerequisite) by ID.
     * Uses the concrete subtype repositories because Hibernate 6.6+ has issues with
     * findById (EntityManager.find) and JPQL queries on the abstract CourseCompetency
     * SINGLE_TABLE base class.
     *
     * @param competencyId the ID to look up
     * @return the found CourseCompetency
     * @throws EntityNotFoundException if no Competency or Prerequisite with the given ID exists
     */
    public CourseCompetency findCompetencyOrPrerequisiteByIdElseThrow(long competencyId) {
        return competencyRepository.findById(competencyId).map(c -> (CourseCompetency) c).or(() -> prerequisiteRepository.findById(competencyId).map(p -> (CourseCompetency) p))
                .orElseThrow(() -> new EntityNotFoundException("CourseCompetency", competencyId));
    }

    public List<CourseCompetency> findAllCompetenciesById(Iterable<Long> ids) {
        return courseCompetencyRepository.findAllById(ids);
    }

    public Set<Competency> findAllCompetenciesByIdsAndCourseId(Set<Long> ids, long courseId) {
        return competencyRepository.findAllByIdsAndCourseId(ids, courseId);
    }
}
