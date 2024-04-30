package de.tum.in.www1.artemis.service.metrics;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.metrics.ExerciseMetricsRepository;
import de.tum.in.www1.artemis.repository.metrics.LectureUnitMetricsRepository;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ExerciseStudentMetricsDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.LectureUnitStudentMetricsDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.ResourceTimestampDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.StudentMetricsDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.SubmissionTimestampDTO;

/**
 * Service class to access metrics regarding students' learning progress.
 */
@Profile(PROFILE_CORE)
@Service
public class MetricsService {

    private final ExerciseMetricsRepository exerciseMetricsRepository;

    private final LectureUnitMetricsRepository lectureUnitMetricsRepository;

    public MetricsService(ExerciseMetricsRepository exerciseMetricsRepository, LectureUnitMetricsRepository lectureUnitMetricsRepository) {
        this.exerciseMetricsRepository = exerciseMetricsRepository;
        this.lectureUnitMetricsRepository = lectureUnitMetricsRepository;
    }

    /**
     * Get the metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        final var exerciseMetricsDTO = getStudentExerciseMetrics(userId, courseId);
        final var lectureUnitMetricsDTO = getStudentLectureUnitMetrics(userId, courseId);
        return new StudentMetricsDTO(exerciseMetricsDTO, lectureUnitMetricsDTO);
    }

    /**
     * Get the exercise metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public ExerciseStudentMetricsDTO getStudentExerciseMetrics(long userId, long courseId) {
        final var exerciseInfo = exerciseMetricsRepository.findAllExerciseInformationByCourseId(courseId);
        // generate map and remove exercises that are not yet started
        final Predicate<ExerciseInformationDTO> started = e -> e.start().isBefore(ZonedDateTime.now());
        final var exerciseInfoMap = exerciseInfo.stream().filter(started).collect(toMap(ExerciseInformationDTO::id, identity()));

        final var exerciseIds = exerciseInfoMap.keySet();

        final var latestSubmission = exerciseMetricsRepository.findLatestSubmissionsForUser(exerciseIds, userId);
        final var latestSubmissionMap = latestSubmission.stream().collect(toMap(ResourceTimestampDTO::id, ResourceTimestampDTO::timestamp));

        final var exerciseStart = exerciseMetricsRepository.findExerciseStartForUser(exerciseIds, userId);
        final var exerciseStartMap = exerciseStart.stream().collect(toMap(ResourceTimestampDTO::id, ResourceTimestampDTO::timestamp));

        final var submissionTimestamps = exerciseMetricsRepository.findSubmissionTimestampsForUser(exerciseIds, userId);
        final var submissionTimestampMap = submissionTimestamps.stream().collect(groupingBy(SubmissionTimestampDTO::exerciseId, mapping(identity(), toSet())));

        return new ExerciseStudentMetricsDTO(exerciseInfoMap, latestSubmissionMap, exerciseStartMap, submissionTimestampMap);
    }

    /**
     * Get the lecture unit metrics for a student in a course.
     *
     * @param userId   the id of the student
     * @param courseId the id of the course
     * @return the metrics for the student in the course
     */
    public LectureUnitStudentMetricsDTO getStudentLectureUnitMetrics(long userId, long courseId) {
        final var lectureUnitInfo = lectureUnitMetricsRepository.findAllLectureUnitInformationByCourseId(courseId);

        // generate map and remove lecture units that are not yet released
        final Predicate<LectureUnitInformationDTO> released = lu -> lu.releaseDate().isBefore(ZonedDateTime.now());
        final var lectureUnitInfoMap = lectureUnitInfo.stream().filter(released).collect(toMap(LectureUnitInformationDTO::id, identity()));

        final var lectureUnitIds = lectureUnitInfoMap.keySet();

        return new LectureUnitStudentMetricsDTO(lectureUnitInfoMap);
    }
}
