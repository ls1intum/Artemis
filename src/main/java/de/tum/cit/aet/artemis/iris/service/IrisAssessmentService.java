package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisUnsupportedExerciseTypeException;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Service for managing state and result of an iris assessment.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(IrisAssessmentService.class);

    private final IrisAssessmentRepository irisAssessmentRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    private final UserRepository userRepository;

    public IrisAssessmentService(IrisAssessmentRepository irisAssessmentRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository, UserRepository userRepository) {
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
        this.userRepository = userRepository;
    }

    public void saveAndHandleVerdict(User user, Exercise exercise, IrisVerdictDTO verdictDTO) {
        saveAndHandleVerdict(user, exercise, verdictDTO, false);
    }

    public void saveAndHandleVerdict(User user, Exercise exercise, IrisVerdictDTO verdictDTO, boolean inClass) {
        IrisAssessment assessment = findOrCreateAssessment(user, exercise, inClass, true);

        assessment.setVerdict(verdictDTO.verdict());
        addReasoningInternal(assessment, verdictDTO.reasoning());
        // Reset review status because of new verdict
        assessment.setVerdictReview(null);

        irisAssessmentRepository.save(assessment);
    }

    public void addReasoning(User user, Exercise exercise, String reasoning) {
        addReasoning(user, exercise, reasoning, false);
    }

    public void addReasoning(User user, Exercise exercise, String reasoning, boolean inClass) {
        IrisAssessment assessment = findOrCreateAssessment(user, exercise, inClass, true);
        addReasoningInternal(assessment, reasoning);
        irisAssessmentRepository.save(assessment);
    }

    private void addReasoningInternal(IrisAssessment assessment, String reasoning) {
        var reasonings = assessment.getReasoning() == null ? new ArrayList<String>() : assessment.getReasoning();

        reasonings.add(reasoning);
        assessment.setReasoning(reasonings);
    }

    public boolean assessmentAttentionNeededInCourse(long courseId) {
        return irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(courseId, IrisVerdict.SUSPICIOUS);
    }

    public void resetVerdictAndReasoning(User user, Exercise exercise) {
        resetVerdictAndReasoning(user, exercise, false);
    }

    public void resetVerdictAndReasoning(User user, Exercise exercise, boolean inClass) {
        IrisAssessment assessment = findOrCreateAssessment(user, exercise, inClass, false);

        assessment.setVerdict(null);
        assessment.setReasoning(new ArrayList<>());
        irisAssessmentRepository.save(assessment);
    }

    public void resetVerdictAndReasoning(IrisAssessment assessment) {
        assessment.setVerdict(null);
        assessment.setReasoning(new ArrayList<>());
        irisAssessmentRepository.save(assessment);
    }

    /**
     * Accepts the answers in the given {@link IrisAssessment} by updating the review status accordingly.
     *
     * @param assessment the assessment to update
     * @throws Error if the verdict saved in assessment is invalid
     */
    public void acceptAnswers(IrisAssessment assessment) {
        // If answers were already accepted, nothing must be done
        if (assessment.getVerdictReview() == IrisVerdictReview.ACCEPTED) {
            return;
        }

        if (assessment.getVerdict() == null) {
            throw new Error("Tried to accept answers for assessment where verdict is null");
        }

        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);
        irisAssessmentRepository.save(assessment);
    }

    /**
     * Rejects the answers in the given {@link IrisAssessment} by updating the review status accordingly.
     *
     * @param assessment the assessment to update
     * @throws Error if the verdict saved in assessment is invalid
     */
    public void rejectAnswers(IrisAssessment assessment) {
        // If answers were already rejected, nothing must be done
        if (assessment.getVerdictReview() == IrisVerdictReview.REJECTED) {
            return;
        }

        if (assessment.getVerdict() == null) {
            throw new Error("Tried to reject answers for assessment where verdict is null");
        }

        assessment.setVerdictReview(IrisVerdictReview.REJECTED);
        irisAssessmentRepository.save(assessment);
    }

    public void handleEventFromIris(TrackedSessionBasedPyrisJob job, PyrisChatStatusUpdateDTO statusUpdate) {
        if (statusUpdate.event() == null) {
            return;
        }

        var session = irisExerciseChatSessionRepository.findByIdElseThrow(job.sessionId());
        var user = userRepository.findByIdElseThrow(session.getUserId());
        Exercise exercise = programmingExerciseRepository.findByIdElseThrow(session.getExerciseId());
        user.hasAcceptedExternalLLMUsageElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (exercise.isExamExercise()) {
            throw new IrisUnsupportedExerciseTypeException("Iris is not supported for exam exercises");
        }

        var inClassQuiz = session.isInClassQuiz();

        switch (IrisPipeEvent.valueOf(statusUpdate.event())) {
            case IrisPipeEvent.PROMPTING_FINISHED:
                irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
                session.setInPromptingModePipeline(false);
                session.setInClassQuiz(false);
                irisExerciseChatSessionRepository.save(session);

                try {
                    if (statusUpdate.verdict() == null) {
                        throw new Error("Prompting finished without verdict");
                    }
                    saveAndHandleVerdict(user, exercise, statusUpdate.verdict(), inClassQuiz);
                }
                catch (Exception e) {
                    log.error("Error while processing prompting mode verdict and reasoning {}", statusUpdate.verdict(), e);
                }
                break;
            case IrisPipeEvent.NEXT_QUESTION:
                try {
                    irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
                    if (statusUpdate.verdict() == null) {
                        throw new Error("Answer has no verdict");
                    }
                    addReasoning(user, exercise, statusUpdate.verdict().reasoning(), inClassQuiz);

                    session.setQuestionsAsked(session.getQuestionsAsked() + 1);
                    irisExerciseChatSessionRepository.save(session);
                }
                catch (Exception e) {
                    log.error("Error while processing prompting mode reasoning {}", statusUpdate.verdict(), e);
                }
                break;
            case IrisPipeEvent.FIRST_QUESTION:
                try {
                    irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);

                    session.setQuestionsAsked(session.getQuestionsAsked() + 1);
                    irisExerciseChatSessionRepository.save(session);
                }
                catch (Exception e) {
                    log.error("Error while processing first question pipeline callback {}", statusUpdate.verdict(), e);
                }
                break;
            default:
                break;
        }
    }

    public IrisAssessment createNewAssessment(ProgrammingExerciseStudentParticipation participation) {
        return createNewAssessment(participation, false);
    }

    public IrisAssessment createNewAssessment(ProgrammingExerciseStudentParticipation participation, boolean inClass) {
        var student = participation.getStudent().orElseThrow();
        var exercise = participation.getExercise();

        var newAssessment = irisAssessmentRepository.save(new IrisAssessment(student, exercise));

        if (inClass) {
            participation.setIrisAssessmentInClass(newAssessment);
        }
        else {
            participation.setIrisAssessment(newAssessment);
        }
        programmingExerciseStudentParticipationRepository.save(participation);

        return newAssessment;

    }

    public void deleteInClassAssessmentsForExercise(ProgrammingExercise exercise) {
        var assessmentIds = programmingExerciseStudentParticipationRepository.findIrisAssessmentInClassIdsByExerciseId(exercise.getId());
        if (assessmentIds.isEmpty()) {
            return;
        }

        programmingExerciseStudentParticipationRepository.unsetIrisAssessmentInClassByExerciseId(exercise.getId());
        irisAssessmentRepository.deleteAllByIdInBulk(assessmentIds);
    }

    private IrisAssessment findOrCreateAssessment(User user, Exercise exercise, boolean inClass, boolean withReasoning) {
        var participation = programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin(), inClass)
                .orElseThrow();
        var assessment = inClass ? participation.getIrisAssessmentInClass() : participation.getIrisAssessment();

        if (assessment == null) {
            return createNewAssessment(participation, inClass);
        }

        if (withReasoning) {
            return irisAssessmentRepository.findWithReasoningById(assessment.getId()).orElseThrow();
        }

        return assessment;
    }
}
