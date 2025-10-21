package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardSettingDTO;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardWithCurrentUserEntryDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionTrainingDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizQuestionProgressService;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingLeaderboardService;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizTrainingResource {

    private static final Logger log = LoggerFactory.getLogger(QuizTrainingResource.class);

    private final QuizTrainingLeaderboardService quizTrainingLeaderboardService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final QuizQuestionProgressService quizQuestionProgressService;

    private final QuizTrainingService quizTrainingService;

    public QuizTrainingResource(QuizTrainingLeaderboardService quizTrainingLeaderboardService, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService, QuizQuestionProgressService quizQuestionProgressService, QuizTrainingService quizTrainingService) {
        this.quizTrainingLeaderboardService = quizTrainingLeaderboardService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.quizQuestionProgressService = quizQuestionProgressService;
        this.quizTrainingService = quizTrainingService;
    }

    /**
     * Retrieves the quiz questions for the training session for the given course. The questions are selected based on the spaced repetition algorithm.
     *
     * @param courseId     the id of the course whose quiz questions should be retrieved
     * @param pageable     pagination information
     * @param isNewSession whether a new training session is being started
     * @param questionIds  optional set of question IDs to filter the questions
     * @return a list of quiz questions for the training session depending on the pagination information
     */
    @PostMapping("courses/{courseId}/training-questions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<QuizQuestionTrainingDTO>> getQuizQuestionsForPractice(@PathVariable long courseId, Pageable pageable, @RequestParam boolean isNewSession,
            @RequestBody Set<Long> questionIds) {
        log.info("REST request to get quiz questions for course with id : {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        Slice<QuizQuestionTrainingDTO> quizQuestionsSlice = quizQuestionProgressService.getQuestionsForSession(courseId, user.getId(), pageable, questionIds, isNewSession);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Has-Next", Boolean.toString(quizQuestionsSlice.hasNext()));
        return new ResponseEntity<>(quizQuestionsSlice.getContent(), headers, HttpStatus.OK);
    }

    /**
     * POST /courses/:courseId/training/:quizQuestionId/submit: Submit a new quizQuestion for training mode.
     *
     * @param courseId        the id of the course containing the quiz question
     * @param quizQuestionId  the id of the quiz question which is being answered
     * @param isRated         whether the submitted answer should be rated (i.e. affect the user's progress= or not
     * @param submittedAnswer the submitted answer by the user for the quiz question
     * @return the ResponseEntity with status 200 (OK) and the result of the evaluated submitted answer as its body
     */
    @PostMapping("courses/{courseId}/training-questions/{quizQuestionId}/submit")
    @EnforceAtLeastStudent
    public ResponseEntity<SubmittedAnswerAfterEvaluationDTO> submitForTraining(@PathVariable long courseId, @PathVariable long quizQuestionId, @RequestParam boolean isRated,
            @Valid @RequestBody SubmittedAnswer submittedAnswer) {
        log.debug("REST request to submit QuizQuestion for training : {}", submittedAnswer);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        ZonedDateTime answeredAt = ZonedDateTime.now();

        SubmittedAnswerAfterEvaluationDTO result = quizTrainingService.submitForTraining(quizQuestionId, user.getId(), courseId, submittedAnswer, isRated, answeredAt);

        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves the leaderboard for quiz training in a specific course.
     *
     * <p>
     * This endpoint returns a list of leaderboard entries for the given course,
     * showing the ranking of users based on their quiz training performance.
     * </p>
     *
     * @param courseId the id of the course for which the leaderboard is requested
     * @return the ResponseEntity containing a list of LeaderboardEntryDTO objects representing the leaderboard
     */
    @GetMapping("courses/{courseId}/training/leaderboard")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<LeaderboardWithCurrentUserEntryDTO> getQuizTrainingLeaderboard(@PathVariable long courseId) {
        log.info("REST request to get leaderboard for course with id : {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        LeaderboardWithCurrentUserEntryDTO leaderboard = quizTrainingLeaderboardService.getLeaderboard(user.getId(), courseId);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Updates the leaderboard visibility settings for the current user.
     *
     * <p>
     * This endpoint allows a user to update their preference for being shown in the leaderboard.
     * If the `showInLeaderboard` property is provided in the request body, the user's setting is updated accordingly.
     * </p>
     *
     * @param leaderboardSettingDTO the DTO containing the leaderboard visibility setting
     * @return a ResponseEntity with HTTP status 200 (OK)
     */
    @PutMapping("leaderboard-settings")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> updateLeaderboardSettings(@Valid @RequestBody LeaderboardSettingDTO leaderboardSettingDTO) {
        log.debug("Rest request to set leaderboard settings: {}", leaderboardSettingDTO);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Boolean shownInLeaderboard = leaderboardSettingDTO.showInLeaderboard();
        if (shownInLeaderboard != null) {
            quizTrainingLeaderboardService.updateShowInLeaderboard(user.getId(), shownInLeaderboard);
        }
        return ResponseEntity.ok().build();
    }
}
