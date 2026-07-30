package de.tum.cit.aet.artemis.iris.service;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.promptuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
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
@Conditional(IrisEnabled.class)
public class IrisAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(IrisAssessmentService.class);

    private final IrisAssessmentRepository irisAssessmentRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final UserRepository userRepository;

    public IrisAssessmentService(IrisAssessmentRepository irisAssessmentRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, IrisSettingsService irisSettingsService,
            AuthorizationCheckService authCheckService, IrisChatSessionRepository irisChatSessionRepository, UserRepository userRepository) {
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Saves the Iris verdict for a user's regular assessment.
     *
     * @param user       the assessed user
     * @param exercise   the exercise
     * @param verdictDTO the verdict payload
     */
    public void saveAndHandleVerdict(User user, Exercise exercise, IrisVerdictDTO verdictDTO) {
        saveAndHandleVerdict(user, exercise, verdictDTO, false);
    }

    /**
     * Saves the Iris verdict for a user's assessment.
     *
     * @param user       the assessed user
     * @param exercise   the exercise
     * @param verdictDTO the verdict payload
     * @param inClass    whether to use the in-class assessment
     */
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

    /**
     * Clears verdict and reasoning for a user's regular assessment.
     *
     * @param user     the assessed user
     * @param exercise the exercise
     */
    public void resetVerdictAndReasoning(User user, Exercise exercise) {
        resetVerdictAndReasoning(user, exercise, false);
    }

    /**
     * Clears verdict and reasoning for a user's assessment.
     *
     * @param user     the assessed user
     * @param exercise the exercise
     * @param inClass  whether to use the in-class assessment
     */
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

    /**
     * Creates a regular Iris assessment for the given participation.
     *
     * @param participation the participation
     * @return the created assessment
     */
    public IrisAssessment createNewAssessment(ProgrammingExerciseStudentParticipation participation) {
        return createNewAssessment(participation, false);
    }

    /**
     * Creates an Iris assessment for the given participation.
     *
     * @param participation the participation
     * @param inClass       whether to create an in-class assessment
     * @return the created assessment
     */
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

    /**
     * Deletes all in-class Iris assessments for an exercise and clears the participation references first.
     *
     * @param exercise the programming exercise
     */
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
