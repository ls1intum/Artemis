package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseForCourseDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * REST controller to retrieve information about quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseRetrievalResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseRetrievalResource.class);

    private final AuthorizationCheckService authCheckService;

    private final QuizBatchService quizBatchService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseService quizExerciseService;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ChannelRepository channelRepository;

    private final QuizBatchRepository quizBatchRepository;

    public QuizExerciseRetrievalResource(QuizExerciseRepository quizExerciseRepository, QuizExerciseService quizExerciseService, UserRepository userRepository,
            AuthorizationCheckService authCheckService, StudentParticipationRepository studentParticipationRepository, QuizBatchService quizBatchService,
            ChannelRepository channelRepository, QuizBatchRepository quizBatchRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseService = quizExerciseService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.quizBatchService = quizBatchService;
        this.channelRepository = channelRepository;
        this.quizBatchRepository = quizBatchRepository;
    }

    /**
     * GET /courses/:courseId/quiz-exercises : get all the exercises.
     *
     * @param courseId id of the course of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of quiz exercises in body
     */
    @GetMapping("courses/{courseId}/quiz-exercises")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<List<QuizExerciseForCourseDTO>> getQuizExercisesForCourse(@PathVariable Long courseId) {
        log.info("REST request to get all quiz exercises for the course with id : {}", courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var quizExercises = quizExerciseRepository.findByCourseIdWithCategories(courseId);
        var quizExerciseDTOs = new ArrayList<QuizExerciseForCourseDTO>();
        for (QuizExercise quizExercise : quizExercises) {
            setQuizBatches(user, quizExercise);
            boolean isEditable = quizExerciseService.isEditable(quizExercise);
            quizExerciseDTOs.add(QuizExerciseForCourseDTO.of(quizExercise, isEditable));
        }

        return ResponseEntity.ok(quizExerciseDTOs);
    }

    /**
     * GET /:examId/quiz-exercises : get all the quiz exercises of an exam.
     *
     * @param examId id of the exam of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of quiz exercises in body
     */
    @GetMapping("exams/{examId}/quiz-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<List<QuizExerciseForCourseDTO>> getQuizExercisesForExam(@PathVariable Long examId) {
        log.info("REST request to get all quiz exercises for the exam with id : {}", examId);
        List<QuizExercise> quizExercises = quizExerciseRepository.findByExamId(examId);
        List<QuizExerciseForCourseDTO> quizExerciseDTOs = new ArrayList<>();
        Course course = quizExercises.getFirst().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        for (QuizExercise quizExercise : quizExercises) {
            boolean isEditable = false;
            quizExerciseDTOs.add(QuizExerciseForCourseDTO.of(quizExercise, isEditable));
        }
        return ResponseEntity.ok(quizExerciseDTOs);
    }

    /**
     * GET /quiz-exercises/:quizExerciseId : get the quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("quiz-exercises/{quizExerciseId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long quizExerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.info("REST request to get quiz exercise : {}", quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(quizExerciseId);
        if (quizExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, quizExercise, user);
            studentParticipationRepository.checkTestRunsExist(quizExercise);
        }
        if (quizExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(quizExercise.getId());
            if (channel != null) {
                quizExercise.setChannelName(channel.getName());
            }
        }
        setQuizBatches(user, quizExercise);
        return ResponseEntity.ok(quizExercise);
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/for-student : get the quizExercise with a particular batch. (information filtered for students)
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("quiz-exercises/{quizExerciseId}/for-student")
    @EnforceAtLeastStudent
    public ResponseEntity<QuizExercise> getQuizExerciseForStudent(@PathVariable Long quizExerciseId) {
        log.info("REST request to get quiz exercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToSeeCourseExercise(quizExercise, user)) {
            throw new AccessForbiddenException();
        }
        quizExercise.setQuizBatches(null); // remove proxy and load batches only if required
        var batch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin());
        log.info("Found batch {} for user {}", batch.orElse(null), user.getLogin());
        quizExercise.setQuizBatches(batch.stream().collect(Collectors.toSet()));
        // filter out information depending on quiz state
        quizExercise.applyAppropriateFilterForStudents(batch.orElse(null));

        return ResponseEntity.ok(quizExercise);
    }

    /**
     * Search for all quiz exercises by id, title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("quiz-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<QuizExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(quizExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    private void setQuizBatches(User user, QuizExercise quizExercise) {
        if (quizExercise.getQuizMode() != null) {
            Set<QuizBatch> batches = switch (quizExercise.getQuizMode()) {
                case SYNCHRONIZED -> quizBatchRepository.findAllByQuizExercise(quizExercise);
                case BATCHED -> quizBatchRepository.findAllByQuizExerciseAndCreator(quizExercise, user.getId());
                case INDIVIDUAL -> Set.of();
            };
            quizExercise.setQuizBatches(batches);
        }
    }
}
