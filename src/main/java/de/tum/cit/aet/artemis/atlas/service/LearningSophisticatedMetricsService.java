package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyStudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.ExerciseStudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitStudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;

/**
 * Service class to access metrics regarding students' learning progress.
 */
@Profile(PROFILE_ATLAS)
@Service
public class LearningSophisticatedMetricsService {

    /**
     * Get the metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        ExerciseStudentMetricsDTO exerciseMetricsDTO = null;
        LectureUnitStudentMetricsDTO lectureUnitMetricsDTO = null;
        CompetencyStudentMetricsDTO competencyMetricsDTO = null;
        return new StudentMetricsDTO(exerciseMetricsDTO, lectureUnitMetricsDTO, competencyMetricsDTO);
    }
}
