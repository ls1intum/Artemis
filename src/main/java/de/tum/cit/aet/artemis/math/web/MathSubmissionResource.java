package de.tum.cit.aet.artemis.math.web;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

@Lazy
@Conditional(MathEnabled.class)
@RestController
@RequestMapping("api/math/")
public class MathSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(MathSubmissionResource.class);

    private final MathSubmissionRepository mathSubmissionRepository;

    private final MathExerciseRepository mathExerciseRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    public MathSubmissionResource(MathSubmissionRepository mathSubmissionRepository, MathExerciseRepository mathExerciseRepository, UserRepository userRepository,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService) {
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.mathExerciseRepository = mathExerciseRepository;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authCheckService = authCheckService;
    }

    @PostMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> createMathSubmission(@PathVariable Long exerciseId, @RequestBody MathSubmissionDTO mathSubmissionDTO) {
        log.debug("REST request to save MathSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(save(exerciseId, mathSubmissionDTO));
    }

    @PutMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> updateMathSubmission(@PathVariable Long exerciseId, @RequestBody MathSubmissionDTO mathSubmissionDTO) {
        log.debug("REST request to update MathSubmission for exercise : {}", exerciseId);
        return ResponseEntity.ok(save(exerciseId, mathSubmissionDTO));
    }

    private MathSubmissionDTO save(Long exerciseId, MathSubmissionDTO dto) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        MathExercise mathExercise = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        // Re-check current course membership: a StudentParticipation persists after un-enrollment, so its existence alone is not sufficient authorization.
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, mathExercise, user);
        StudentParticipation participation = studentParticipationRepository.findFirstByExerciseIdAndStudentLoginOrderByIdDesc(exerciseId, user.getLogin()).orElseThrow();

        MathSubmission submission = dto.toEntity();
        submission.setParticipation(participation);
        submission = mathSubmissionRepository.save(submission);
        submission = mathSubmissionRepository.findByIdWithResults(submission.getId()).orElseThrow();

        participation.setExercise(mathExercise);
        submission.setParticipation(participation);
        return MathSubmissionDTO.of(submission);
    }

    /**
     * GET /participations/:participationId/math-editor : Returns the data needed for the math editor,
     * including the participation, the latest MathSubmission, and results.
     *
     * @param participationId the participation for which to load editor data
     * @return ResponseEntity with the latest MathSubmission (empty if no submission yet)
     */
    @GetMapping("participations/{participationId}/math-editor")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> getDataForMathEditor(@PathVariable Long participationId) {
        log.debug("REST request to get math editor data for participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithLatestSubmissionsResultsFeedbackElseThrow(participationId);

        if (!(participation.getExercise() instanceof MathExercise mathExercise)) {
            throw new IllegalArgumentException("Participation does not belong to a math exercise");
        }
        // Reload with categories to avoid LazyInitializationException when DTO is serialized/logged
        mathExercise = mathExerciseRepository.findByIdWithCategories(mathExercise.getId()).orElseThrow();
        participation.setExercise(mathExercise);
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(mathExercise))) {
            throw new AccessForbiddenException("participation", participationId);
        }

        Optional<MathSubmission> latestSubmission = participation.findLatestSubmission().filter(s -> s instanceof MathSubmission).map(s -> (MathSubmission) s);

        MathSubmission submission;
        submission = latestSubmission.map(mathSubmission -> mathSubmissionRepository.findByIdWithResults(mathSubmission.getId()).orElseThrow()).orElseGet(MathSubmission::new);
        submission.setParticipation(participation);
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * GET /math-submissions/{submissionId} : get a single submission; restricted to its owner or a tutor of the exercise.
     *
     * @param submissionId the id of the submission to retrieve
     * @return the submission
     */
    @GetMapping("math-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<MathSubmissionDTO> getMathSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get MathSubmission : {}", submissionId);
        MathSubmission submission = mathSubmissionRepository.findByIdWithResultsAndParticipation(submissionId).orElseThrow();
        if (!(submission.getParticipation() instanceof StudentParticipation participation) || !(participation.getExercise() instanceof MathExercise mathExercise)) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(mathExercise))) {
            throw new AccessForbiddenException("mathSubmission", submissionId);
        }
        return ResponseEntity.ok(MathSubmissionDTO.of(submission));
    }

    /**
     * GET /exercises/{exerciseId}/math-submissions : list all submitted submissions for an exercise.
     *
     * @param exerciseId the exercise whose submissions to list
     * @return submitted submissions for the exercise
     */
    @GetMapping("exercises/{exerciseId}/math-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MathSubmissionDTO>> getSubmittedMathSubmissions(@PathVariable Long exerciseId) {
        log.debug("REST request to get submitted MathSubmissions for exercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        List<MathSubmissionDTO> dtos = mathSubmissionRepository.findSubmittedByExerciseId(exerciseId).stream().map(MathSubmissionDTO::of).toList();
        return ResponseEntity.ok(dtos);
    }
}
