package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.api.ExamMetricsApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeCountDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

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

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final FaqRepository faqRepository;

    private final ConversationRepository conversationRepository;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<CompetencyApi> competencyApi;

    public CourseAdminService(BuildJobRepository buildJobRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<ExamMetricsApi> examMetricsApi, ExerciseRepository exerciseRepository, UserRepository userRepository,
            CourseRepository courseRepository, ParticipationRepository participationRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, Optional<CompetencyProgressApi> competencyProgressApi, Optional<LearnerProfileApi> learnerProfileApi,
            Optional<IrisSettingsApi> irisSettingsApi, FaqRepository faqRepository, ConversationRepository conversationRepository, Optional<TutorialGroupApi> tutorialGroupApi,
            Optional<CompetencyApi> competencyApi) {
        this.buildJobRepository = buildJobRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.examMetricsApi = examMetricsApi;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.learnerProfileApi = learnerProfileApi;
        this.irisSettingsApi = irisSettingsApi;
        this.faqRepository = faqRepository;
        this.conversationRepository = conversationRepository;
        this.tutorialGroupApi = tutorialGroupApi;
        this.competencyApi = competencyApi;
    }

    /**
     * Get a comprehensive summary of all course data.
     * This summary is used for both deletion and reset operations to provide
     * administrators with an overview of what will be affected.
     *
     * @param courseId the id of the course for which to get the summary
     * @return the course summary containing counts of all relevant data
     */
    public CourseSummaryDTO getCourseSummary(long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);

        // Users
        long numberOfStudents = userRepository.countUserInGroup(course.getStudentGroupName());
        long numberOfTutors = userRepository.countUserInGroup(course.getTeachingAssistantGroupName());
        long numberOfEditors = userRepository.countUserInGroup(course.getEditorGroupName());
        long numberOfInstructors = userRepository.countUserInGroup(course.getInstructorGroupName());

        // Student Work
        long numberOfParticipations = participationRepository.countByCourseId(courseId);
        long numberOfSubmissions = submissionRepository.countByCourseId(courseId);
        Set<Long> exerciseIds = exerciseRepository.findExerciseIdsByCourseId(courseId);
        long numberOfResults = exerciseIds.isEmpty() ? 0L : resultRepository.countByExerciseIds(exerciseIds);

        // Communication
        long numberOfConversations = conversationRepository.countByCourseId(courseId);
        long numberOfPosts = postRepository.countPostsByCourseId(courseId);
        long numberOfAnswerPosts = answerPostRepository.countAnswerPostsByCourseId(courseId);

        // Learning Data
        long numberOfCompetencies = competencyApi.map(api -> api.countByCourseId(courseId)).orElse(0L);
        long numberOfCompetencyProgress = competencyProgressApi.map(api -> api.countCompetencyProgressByCourseId(courseId)).orElse(0L);
        long numberOfLearnerProfiles = learnerProfileApi.map(api -> api.countByCourseId(courseId)).orElse(0L);

        // AI Data
        long numberOfIrisChatSessions = irisSettingsApi.map(api -> api.countCourseChatSessionsByCourseId(courseId)).orElse(0L);
        long numberOfLLMTraces = llmTokenUsageTraceRepository.countByCourseId(courseId);

        // Infrastructure
        long numberOfBuilds = buildJobRepository.countBuildJobsByCourseId(courseId);

        // Course Structure
        long numberOfExams = examMetricsApi.map(api -> api.countByCourseId(courseId)).orElse(0L);
        long numberOfLectures = lectureRepositoryApi.map(api -> api.countByCourseId(courseId)).orElse(0L);
        long numberOfFaqs = faqRepository.countByCourseId(courseId);
        long numberOfTutorialGroups = tutorialGroupApi.map(api -> api.countByCourseId(courseId)).orElse(0L);

        // Exercises
        Map<ExerciseType, Long> countByExerciseType = countByCourseIdGroupByType(courseId);
        long numberOfProgrammingExercises = countByExerciseType.get(ExerciseType.PROGRAMMING);
        long numberOfTextExercises = countByExerciseType.get(ExerciseType.TEXT);
        long numberOfModelingExercises = countByExerciseType.get(ExerciseType.MODELING);
        long numberOfQuizExercises = countByExerciseType.get(ExerciseType.QUIZ);
        long numberOfFileUploadExercises = countByExerciseType.get(ExerciseType.FILE_UPLOAD);
        long numberOfExercises = countByExerciseType.values().stream().mapToLong(Long::longValue).sum();

        return new CourseSummaryDTO(numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors, numberOfParticipations, numberOfSubmissions, numberOfResults,
                numberOfConversations, numberOfPosts, numberOfAnswerPosts, numberOfCompetencies, numberOfCompetencyProgress, numberOfLearnerProfiles, numberOfIrisChatSessions,
                numberOfLLMTraces, numberOfBuilds, numberOfExams, numberOfExercises, numberOfProgrammingExercises, numberOfTextExercises, numberOfModelingExercises,
                numberOfQuizExercises, numberOfFileUploadExercises, numberOfLectures, numberOfFaqs, numberOfTutorialGroups);
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
