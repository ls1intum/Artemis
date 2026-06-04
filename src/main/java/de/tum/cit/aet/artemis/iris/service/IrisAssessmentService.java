package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisPipeEvent;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.TrackedSessionBasedPyrisJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisUnsupportedExerciseTypeException;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
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

    private final StudentScoreRepository studentScoreRepository;

    public IrisAssessmentService(IrisAssessmentRepository irisAssessmentRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository, UserRepository userRepository,
            StudentScoreRepository studentScoreRepository) {
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
        this.userRepository = userRepository;
        this.studentScoreRepository = studentScoreRepository;
    }

    @Transactional
    public void saveAndHandleVerdict(User user, Exercise exercise, IrisVerdictDTO verdict) {
        IrisAssessment assessment = irisAssessmentRepository.findWithReasoningByExerciseIdAndStudentId(exercise.getId(), user.getId()).orElseThrow();

        String verdictString = verdict.verdict();
        assessment.setVerdict(verdictString);

        switch (verdictString) {
            case "unsuspicious":
                updateVerifiedScoreUnsuspicious(user, exercise, assessment);
                assessment.setVerdictReview(IrisVerdictReview.REVIEWABLE);
                break;
            case "suspicious":
                updateVerifiedScoreSuspicious(user, exercise, assessment);
                assessment.setVerdictReview(IrisVerdictReview.NEEDS_REVIEW);
                break;
            default:
                throw new Error("unknown verdict: " + verdict);

        }

        addReasoningInternal(assessment, verdict.reasoning());
        irisAssessmentRepository.save(assessment);
    }

    @Transactional
    public void addReasoning(User user, Exercise exercise, String reasoning) {
        IrisAssessment assessment = irisAssessmentRepository.findWithReasoningByExerciseIdAndStudentId(exercise.getId(), user.getId()).orElseThrow();
        addReasoningInternal(assessment, reasoning);
        irisAssessmentRepository.save(assessment);
    }

    private void addReasoningInternal(IrisAssessment assessment, String reasoning) {
        var reasonings = assessment.getReasoning() == null ? new ArrayList<String>() : assessment.getReasoning();

        reasonings.add(reasoning);
        assessment.setReasoning(reasonings);
    }

    private void updateVerifiedScoreUnsuspicious(User user, Exercise exercise, IrisAssessment assessment) {
        // TODO: maybe load here participation with also submissions.results as attribute path and not only submissions if none are found
        var participation = programmingExerciseStudentParticipationRepository.findWithSubmissionsAndResultsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin())
                .orElseThrow();
        Double recentScore = participation.findLatestResult().getScore();
        if (assessment.getVerifiedScore() == null || recentScore > assessment.getVerifiedScore()) {
            assessment.setVerifiedScoreOld(assessment.getVerifiedScore());
            assessment.setVerifiedScore(recentScore);
        }
    }

    private void updateVerifiedScoreSuspicious(User user, Exercise exercise, IrisAssessment assessment) {
        // TODO: maybe load here participation with also submissions.results as attribute path and not only submissions if none are found
        var participation = programmingExerciseStudentParticipationRepository.findWithSubmissionsAndResultsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin())
                .orElseThrow();
        Double recentScore = participation.findLatestResult().getScore();
        if (assessment.getVerifiedScore() == null || recentScore > assessment.getVerifiedScore()) {
            assessment.setVerifiedScoreOld(recentScore);
        }
    }

    public boolean assessmentAttentionNeededInCourse(Course course) {
        return programmingExerciseRepository.findAllWithStudentParticipationsByCourseId(course.getId()).stream().flatMap(exercise -> exercise.getStudentParticipations().stream())
                .map(p -> ((ProgrammingExerciseStudentParticipation) p).getIrisAssessment()).anyMatch(a -> a != null && a.getVerdictReview() == IrisVerdictReview.NEEDS_REVIEW);
    }

    @Transactional
    public void resetVerdictAndReasoning(User user, Exercise exercise) {
        IrisAssessment assessment = irisAssessmentRepository.findByExerciseIdAndStudentId(exercise.getId(), user.getId()).orElseThrow();

        assessment.setVerdict(null);
        assessment.setReasoning(new ArrayList<>());
        irisAssessmentRepository.save(assessment);
    }

    /**
     * Accepts the answers in the given {@link IrisAssessment}.
     *
     * <p>
     * This means, if answers were assessed as suspicious (by Iris) or rejected (by instructor) before, contents of irisVerifiedScore
     * and irisOldVerifiedScore are swapped to have the correct score as verified.
     * </p>
     *
     * @param assessment the assessment to update
     * @return the updated assessment
     * @throws Error if the verdict saved in assessment is invalid
     */
    public IrisAssessment acceptAnswers(IrisAssessment assessment) {
        var verdictReview = assessment.getVerdictReview();

        // If answers were already accepted, nothing must be done
        if (verdictReview == IrisVerdictReview.ACCEPTED) {
            return assessment;
        }

        var verdict = assessment.getVerdict();

        if (verdict == null) {
            throw new Error("verdict is null");
        }
        else if (verdictReview == IrisVerdictReview.REJECTED || verdict.equals("suspicious")) {
            swapVerifiedScoreWithOld(assessment);
        }
        else if (!verdict.equals("unsuspicious")) {
            throw new Error("unknown verdict: " + verdict);
        }

        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);
        return irisAssessmentRepository.save(assessment);
    }

    /**
     * Rejects the answers in the given {@link IrisAssessment}.
     *
     * <p>
     * This means, if answers were assessed as unsuspicious (by Iris) or accepted (by instructor) before, contents of irisVerifiedScore
     * and irisOldVerifiedScore are swapped to have the correct score as verified.
     * </p>
     *
     * @param assessment the assessment to update
     * @return the updated assessment
     * @throws Error if the verdict saved in assessment is invalid
     */
    public IrisAssessment rejectAnswers(IrisAssessment assessment) {
        var verdictReview = assessment.getVerdictReview();

        // If answers were already rejected, nothing must be done
        if (verdictReview == IrisVerdictReview.REJECTED) {
            return assessment;
        }

        var verdict = assessment.getVerdict();

        if (verdict == null) {
            throw new Error("verdict is null");
        }
        else if (verdictReview == IrisVerdictReview.ACCEPTED || verdict.equals("unsuspicious")) {
            swapVerifiedScoreWithOld(assessment);
        }
        else if (!verdict.equals("suspicious")) {
            throw new Error("unknown verdict: " + verdict);
        }

        assessment.setVerdictReview(IrisVerdictReview.REJECTED);
        return irisAssessmentRepository.save(assessment);
    }

    private void swapVerifiedScoreWithOld(IrisAssessment assessment) {
        var newVerifiedScore = assessment.getVerifiedScoreOld();
        assessment.setVerifiedScoreOld(assessment.getVerifiedScore());
        assessment.setVerifiedScore(newVerifiedScore);
    }

    @Transactional
    public void saveNewLastEvent(String event, User user, Exercise exercise) {
        IrisAssessment assessment = irisAssessmentRepository.findByExerciseIdAndStudentId(exercise.getId(), user.getId()).orElseThrow();
        assessment.setLastEvent(IrisPipeEvent.valueOf(event));
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

        saveNewLastEvent(statusUpdate.event(), user, exercise);

        switch (IrisPipeEvent.valueOf(statusUpdate.event())) {
            case IrisPipeEvent.PROMPTING_FINISHED:
                irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
                session.setInPromptingModePipeline(false);
                irisExerciseChatSessionRepository.save(session);

                try {
                    if (statusUpdate.verdict() == null) {
                        throw new Error("Prompting finished without verdict");
                    }
                    saveAndHandleVerdict(user, exercise, statusUpdate.verdict());
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
                    addReasoning(user, exercise, statusUpdate.verdict().reasoning());

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
        var student = participation.getStudent().orElseThrow();
        var exercise = participation.getExercise();

        var newAssessment = irisAssessmentRepository.save(new IrisAssessment(student, exercise));
        var studentScore = studentScoreRepository.findByExercise_IdAndUser_Id(exercise.getId(), student.getId());

        studentScore.ifPresent(score -> {
            score.setAssessment(newAssessment);
            studentScoreRepository.save(score);
        });

        participation.setIrisAssessment(newAssessment);
        programmingExerciseStudentParticipationRepository.save(participation);

        return newAssessment;

    }

    public Double getVerifiedPoints(IrisAssessment assessment) {
        return assessment.getVerifiedScore() == null ? null
                : RoundingUtil.roundScoreSpecifiedByCourseSettings(assessment.getVerifiedScore() * 0.01 * assessment.getExercise().getMaxPoints(),
                        assessment.getExercise().getCourse());
    }

    public Double getVerifiedPointsOld(IrisAssessment assessment) {
        return assessment.getVerifiedScoreOld() == null ? null
                : RoundingUtil.roundScoreSpecifiedByCourseSettings(assessment.getVerifiedScoreOld() * 0.01 * assessment.getExercise().getMaxPoints(),
                        assessment.getExercise().getCourse());
    }
}
