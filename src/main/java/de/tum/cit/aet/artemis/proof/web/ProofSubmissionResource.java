package de.tum.cit.aet.artemis.proof.web;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.proof.domain.DerivationStep;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO;
import de.tum.cit.aet.artemis.proof.repository.ProofExerciseRepository;
import de.tum.cit.aet.artemis.proof.repository.ProofSubmissionRepository;
import de.tum.cit.aet.artemis.proof.service.ProofGradingService;

@RestController
@RequestMapping("api/proof/")
public class ProofSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(ProofSubmissionResource.class);

    private final ProofSubmissionRepository proofSubmissionRepository;

    private final ProofExerciseRepository proofExerciseRepository;

    private final ResultRepository resultRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    private final ProofGradingService proofGradingService;

    public ProofSubmissionResource(ProofSubmissionRepository proofSubmissionRepository, ProofExerciseRepository proofExerciseRepository, ResultRepository resultRepository,
            UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService,
            ProofGradingService proofGradingService) {
        this.proofSubmissionRepository = proofSubmissionRepository;
        this.proofExerciseRepository = proofExerciseRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authCheckService = authCheckService;
        this.proofGradingService = proofGradingService;
    }

    @PostMapping("exercises/{exerciseId}/proof-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<ProofSubmissionDTO> createProofSubmission(@PathVariable Long exerciseId, @RequestBody ProofSubmissionDTO proofSubmissionDTO) {
        log.debug("REST request to save ProofSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(saveAndEvaluate(exerciseId, proofSubmissionDTO));
    }

    @PutMapping("exercises/{exerciseId}/proof-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<ProofSubmissionDTO> updateProofSubmission(@PathVariable Long exerciseId, @RequestBody ProofSubmissionDTO proofSubmissionDTO) {
        log.debug("REST request to update ProofSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(saveAndEvaluate(exerciseId, proofSubmissionDTO));
    }

    private ProofSubmissionDTO saveAndEvaluate(Long exerciseId, ProofSubmissionDTO dto) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProofExercise proofExercise = proofExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        StudentParticipation participation = studentParticipationRepository.findFirstByExerciseIdAndStudentLoginOrderByIdDesc(exerciseId, user.getLogin()).orElseThrow();

        ProofSubmission submission = dto.toEntity();
        submission.setParticipation(participation);
        for (DerivationStep step : submission.getSteps()) {
            step.setSubmission(submission);
        }

        submission = proofSubmissionRepository.save(submission);
        submission = proofSubmissionRepository.findByIdWithStepsAndResults(submission.getId()).orElseThrow();

        if (Boolean.TRUE.equals(submission.isSubmitted())) {
            Result result = new Result();
            result.setSubmission(submission);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(ZonedDateTime.now());
            result.setRated(true);
            result.setExerciseId(exerciseId);

            double score = proofGradingService.gradeSubmission(proofExercise, submission);
            result.setScore(score, proofExercise.getCourseViaExerciseGroupOrCourseMember());

            resultRepository.save(result);
            submission.addResult(result);
        }

        participation.setExercise(proofExercise);
        submission.setParticipation(participation);
        return ProofSubmissionDTO.of(submission);
    }

    /**
     * GET /participations/:participationId/proof-editor : Returns the data needed for the proof editor,
     * including the participation, the latest ProofSubmission, and results.
     *
     * @param participationId the participation for which to load editor data
     * @return ResponseEntity with the latest ProofSubmission (empty if no submission yet)
     */
    @GetMapping("participations/{participationId}/proof-editor")
    @EnforceAtLeastStudent
    public ResponseEntity<ProofSubmissionDTO> getDataForProofEditor(@PathVariable Long participationId) {
        log.debug("REST request to get proof editor data for participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithLatestSubmissionsResultsFeedbackElseThrow(participationId);

        if (!(participation.getExercise() instanceof ProofExercise proofExercise)) {
            throw new IllegalArgumentException("Participation does not belong to a proof exercise");
        }
        // Reload with categories to avoid LazyInitializationException when DTO is serialized/logged
        proofExercise = proofExerciseRepository.findByIdWithCategories(proofExercise.getId()).orElseThrow();
        participation.setExercise(proofExercise);
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(proofExercise))) {
            throw new AccessForbiddenException("participation", participationId);
        }

        Optional<ProofSubmission> latestSubmission = participation.findLatestSubmission().filter(s -> s instanceof ProofSubmission).map(s -> (ProofSubmission) s);

        ProofSubmission submission;
        if (latestSubmission.isPresent()) {
            submission = proofSubmissionRepository.findByIdWithStepsAndResults(latestSubmission.get().getId()).orElseThrow();
        }
        else {
            submission = new ProofSubmission();
        }
        submission.setParticipation(participation);
        return ResponseEntity.ok(ProofSubmissionDTO.of(submission));
    }

    @GetMapping("proof-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<ProofSubmissionDTO> getProofSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get ProofSubmission : {}", submissionId);
        ProofSubmission submission = proofSubmissionRepository.findById(submissionId).orElseThrow();
        return ResponseEntity.ok(ProofSubmissionDTO.of(submission));
    }

    @GetMapping("proof-submissions/{submissionId}/for-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<ProofSubmissionDTO> getProofSubmissionForAssessment(@PathVariable Long submissionId) {
        log.debug("REST request to get ProofSubmission for assessment : {}", submissionId);
        ProofSubmission submission = proofSubmissionRepository.findWithEagerParticipationExerciseResultsById(submissionId).orElseThrow();
        return ResponseEntity.ok(ProofSubmissionDTO.of(submission));
    }
}
