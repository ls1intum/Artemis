package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

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
     * Finds course competencies by their ids and course id.
     *
     * @param ids      the ids of the course competencies
     * @param courseId the id of the course
     * @return the set of found course competencies
     */
    public Set<CourseCompetency> findCourseCompetenciesByIdsAndCourseId(Set<Long> ids, Long courseId) {
        return courseCompetencyRepository.findByIdInAndCourseId(ids, courseId);
    }
}
