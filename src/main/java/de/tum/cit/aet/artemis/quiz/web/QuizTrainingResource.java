package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.QuizTrainingAnswerDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizQuestionProgressService;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizTrainingResource {

    private static final Logger log = LoggerFactory.getLogger(QuizTrainingResource.class);

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final QuizQuestionProgressService quizQuestionProgressService;

    private final QuizTrainingService quizTrainingService;

    public QuizTrainingResource(UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authCheckService,
            QuizQuestionProgressService quizQuestionProgressService, QuizTrainingService quizTrainingService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.quizQuestionProgressService = quizQuestionProgressService;
        this.quizTrainingService = quizTrainingService;
    }

    /**
     * Retrieves all the quiz questions belonging to a course that are released for practice
     *
     * @param courseId the id of the course whose quiz questions should be retrieved
     * @return a set of quiz questions from the specified course that are released for practice
     */
    @GetMapping("courses/{courseId}/training/quiz")
    @EnforceAtLeastStudent
    public ResponseEntity<List<QuizQuestionWithSolutionDTO>> getQuizQuestionsForPractice(@PathVariable Long courseId) {
        log.info("REST request to get quiz questions for course with id : {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        List<QuizQuestion> quizQuestions = quizQuestionProgressService.getQuestionsForSession(courseId, user.getId());
        List<QuizQuestionWithSolutionDTO> quizQuestionsWithSolutions = quizQuestions.stream().map(QuizQuestionWithSolutionDTO::of).toList();
        return ResponseEntity.ok(quizQuestionsWithSolutions);
    }

    /**
     * POST /exercises/:exerciseId/submissions/training : Submit a new quizSubmission for training mode.
     *
     * @param courseId       the id of the course containing the quiz exercise
     * @param quizQuestionId the id of the quiz question which is being answered
     * @return the ResponseEntity with status 200 (OK) and the result of the submitted answer as its body
     */
    @PostMapping("courses/{courseId}/training/{quizQuestionId}/quiz")
    @EnforceAtLeastStudent
    public ResponseEntity<SubmittedAnswerAfterEvaluationDTO> submitForTraining(@PathVariable Long courseId, @PathVariable Long quizQuestionId,
            @Valid @RequestBody QuizTrainingAnswerDTO submittedAnswer) {
        log.debug("REST request to submit QuizQuestion for training : {}", submittedAnswer);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        SubmittedAnswerAfterEvaluationDTO result = quizTrainingService.submitForTraining(courseId, quizQuestionId, user, submittedAnswer);

        return ResponseEntity.ok(result);
    }
}
