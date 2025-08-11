package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CourseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.api.ExamMetricsApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeCountDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;

/**
 * Service for administrative operations on courses.
 * This service provides methods to retrieve information about the course that are only relevant for admins, such as the deletion summary.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseAdminService {

    private final BuildJobRepository buildJobRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<ExamMetricsApi> examMetricsApi;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public CourseAdminService(BuildJobRepository buildJobRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<ExamMetricsApi> examMetricsApi, ExerciseRepository exerciseRepository, UserRepository userRepository,
            CourseRepository courseRepository) {
        this.buildJobRepository = buildJobRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.examMetricsApi = examMetricsApi;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Get the course deletion summary for the given course.
     *
     * @param courseId the id of the course for which to get the deletion summary
     * @return the course deletion summary
     */
    public CourseDeletionSummaryDTO getDeletionSummary(long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        long numberOfStudents = userRepository.countUserInGroup(course.getStudentGroupName());
        long numberOfTutors = userRepository.countUserInGroup(course.getTeachingAssistantGroupName());
        long numberOfEditors = userRepository.countUserInGroup(course.getEditorGroupName());
        long numberOfInstructors = userRepository.countUserInGroup(course.getInstructorGroupName());

        long numberOfBuilds = buildJobRepository.countBuildJobsByCourseId(courseId);

        long numberOfCommunicationPosts = postRepository.countPostsByCourseId(courseId);
        long numberOfAnswerPosts = answerPostRepository.countAnswerPostsByCourseId(courseId);
        long numberLectures = lectureRepositoryApi.map(api -> api.countByCourseId(courseId)).orElse(0L);
        long numberExams = examMetricsApi.map(api -> api.countByCourseId(courseId)).orElse(0L);

        Map<ExerciseType, Long> countByExerciseType = countByCourseIdGroupByType(courseId);
        long numberProgrammingExercises = countByExerciseType.get(ExerciseType.PROGRAMMING);
        long numberTextExercises = countByExerciseType.get(ExerciseType.TEXT);
        long numberQuizExercises = countByExerciseType.get(ExerciseType.QUIZ);
        long numberFileUploadExercises = countByExerciseType.get(ExerciseType.FILE_UPLOAD);
        long numberModelingExercises = countByExerciseType.get(ExerciseType.MODELING);

        return new CourseDeletionSummaryDTO(numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors, numberOfBuilds, numberOfCommunicationPosts, numberOfAnswerPosts,
                numberProgrammingExercises, numberTextExercises, numberFileUploadExercises, numberModelingExercises, numberQuizExercises, numberExams, numberLectures);
    }

    /**
     * Returns a map from exercise type to count of exercise given a course id.
     *
     * @param courseId the course id
     * @return the mapping from exercise type to course type. If a course has no exercises for a specific type, the map contains an entry for that type with value 0.
     */
    public Map<ExerciseType, Long> countByCourseIdGroupByType(long courseId) {
        Map<ExerciseType, Long> exerciseTypeCountMap = exerciseRepository.countByCourseIdGroupedByType(courseId).stream()
                .collect(Collectors.toMap(ExerciseTypeCountDTO::exerciseType, ExerciseTypeCountDTO::count));

        return Arrays.stream(ExerciseType.values()).collect(Collectors.toMap(type -> type, type -> exerciseTypeCountMap.getOrDefault(type, 0L)));
    }
}
