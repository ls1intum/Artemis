package de.tum.cit.aet.artemis.atlas.api;

import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.core.domain.Course;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class CompetencyImportApi extends AbstractAtlasApi {

    private final CourseCompetencyService courseCompetencyService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public CompetencyImportApi(CourseCompetencyService courseCompetencyService, CourseCompetencyRepository courseCompetencyRepository) {
        this.courseCompetencyService = courseCompetencyService;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    /**
     * Finds all course competencies for a course with their exercises, lecture units, lectures and attachments.
     *
     * @param courseId the id of the course
     * @return set of course competencies
     */
    public Set<CourseCompetency> findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(long courseId) {
        return courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(courseId);
    }

    /**
     * Imports course competencies into a course.
     *
     * @param course             the course to import into
     * @param courseCompetencies the course competencies to import
     * @param importOptions      the import options
     * @return the set of imported course competencies
     */
    public Set<CompetencyWithTailRelationDTO> importCourseCompetencies(Course course, Collection<CourseCompetency> courseCompetencies, CompetencyImportOptionsDTO importOptions) {
        return courseCompetencyService.importCourseCompetencies(course, courseCompetencies, importOptions);
    }
}
