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

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CourseCompetencyApi extends AbstractAtlasApi {

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyRepository competencyRepository;

    public CourseCompetencyApi(CourseCompetencyRepository courseCompetencyRepository, CompetencyRepository competencyRepository) {
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyRepository = competencyRepository;
    }

    public void save(CourseCompetency courseCompetency) {
        courseCompetencyRepository.save(courseCompetency);
    }

    /**
     * Finds all course competencies linked to the given exercise.
     *
     * @param exerciseId the ID of the exercise
     * @return list of course competencies linked to the exercise
     */
    public List<CourseCompetency> findAllByExerciseId(long exerciseId) {
        return courseCompetencyRepository.findAllByExerciseId(exerciseId);
    }

    /**
     * Finds course competencies by their ids and course id.
     *
     * @param ids      the ids of the course competencies
     * @param courseId the id of the course
     * @return the set of found course competencies
     */
    public Set<Competency> findCourseCompetenciesByIdsAndCourseId(Set<Long> ids, long courseId) {
        return competencyRepository.findAllByIdsAndCourseId(ids, courseId);
    }
}
