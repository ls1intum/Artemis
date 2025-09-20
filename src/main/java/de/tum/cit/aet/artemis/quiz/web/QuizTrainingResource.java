package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static tech.jhipster.web.util.PaginationUtil.generatePaginationHttpHeaders;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.quiz.dto.QuizTrainingAnswerDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionTrainingDTO;
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
     * Retrieves the quiz questions for the training session for the given course. The questions are selected based on the spaced repetition algorithm.
     *
     * @param courseId    the id of the course whose quiz questions should be retrieved
     * @param pageable    pagination information
     * @param questionIds optional set of question IDs to filter the questions
     * @return a list of 10 quiz questions for the training session
     */
    @PostMapping("courses/{courseId}/training-questions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<QuizQuestionTrainingDTO>> getQuizQuestionsForPractice(@PathVariable long courseId, Pageable pageable,
            @RequestBody(required = false) Set<Long> questionIds) {
        log.info("REST request to get quiz questions for course with id : {}", courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        if (questionIds != null && questionIds.isEmpty()) {
            questionIds.add(-1L);
        }

        Page<QuizQuestionTrainingDTO> quizQuestionsPage = quizQuestionProgressService.getQuestionsForSession(courseId, user.getId(), pageable, questionIds);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), quizQuestionsPage);
        return new ResponseEntity<>(quizQuestionsPage.getContent(), headers, HttpStatus.OK);
    }

    /**
     * POST /courses/:courseId/training/:quizQuestionId/submit: Submit a new quizQuestion for training mode.
     *
     * @param courseId        the id of the course containing the quiz question
     * @param quizQuestionId  the id of the quiz question which is being answered
     * @param submittedAnswer the submitted answer by the user for the quiz question
     * @return the ResponseEntity with status 200 (OK) and the result of the evaluated submitted answer as its body
     */
    @PostMapping("courses/{courseId}/training-questions/{quizQuestionId}/submit")
    @EnforceAtLeastStudent
    public ResponseEntity<SubmittedAnswerAfterEvaluationDTO> submitForTraining(@PathVariable long courseId, @PathVariable long quizQuestionId,
            @Valid @RequestBody QuizTrainingAnswerDTO submittedAnswer) {
        log.debug("REST request to submit QuizQuestion for training : {}", submittedAnswer);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        ZonedDateTime answeredAt = ZonedDateTime.now();

        SubmittedAnswerAfterEvaluationDTO result = quizTrainingService.submitForTraining(quizQuestionId, user.getId(), courseId, submittedAnswer, answeredAt);

        return ResponseEntity.ok(result);
    }
}
