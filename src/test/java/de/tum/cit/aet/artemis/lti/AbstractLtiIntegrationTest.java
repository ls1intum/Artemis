package de.tum.cit.aet.artemis.lti;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.lti.test_repository.OnlineCourseConfigurationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public abstract class AbstractLtiIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // Repositories
    @Autowired
    protected OnlineCourseConfigurationTestRepository onlineCourseConfigurationRepository;

    // External Repositories
    @Autowired
    protected QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    protected SubmissionTestRepository submissionRepository;

    // External Services
    @Autowired
    protected QuizExerciseService quizExerciseService;

    @Autowired
    protected QuizSubmissionService quizSubmissionService;

    // External Util Services
    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected RequestUtilService request;

    // Misc
    @Autowired
    protected ObjectMapper objectMapper;
}
