package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CourseCompetencyApi extends AbstractAtlasApi {

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CourseCompetencyApi(CourseCompetencyRepository courseCompetencyRepository) {
        this.courseCompetencyRepository = courseCompetencyRepository;
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
}
