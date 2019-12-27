package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class TextSubmissionService extends SubmissionService {

    private final TextSubmissionRepository textSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    private final Optional<TextAssessmentQueueService> textAssessmentQueueService;

    private final SimpMessageSendingOperations messagingTemplate;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository, UserService userService,
            Optional<TextAssessmentQueueService> textAssessmentQueueService, SimpMessageSendingOperations messagingTemplate, AuthorizationCheckService authCheckService) {
        super(submissionRepository, userService, authCheckService);
        this.textSubmissionRepository = textSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles text submissions sent from the client and saves them in the database.
     *
     * @param textSubmission the text submission that should be saved
     * @param textExercise   the corresponding text exercise
     * @param principal      the user principal
     * @return the saved text submission
     */
    @Transactional
    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise textExercise, Principal principal) {
        // Don't allow submissions after the due date (except if the exercise was started after the due date)
        final var dueDate = textExercise.getDueDate();
        final var optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginAnyState(textExercise.getId(), principal.getName());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + textExercise.getId());
        }
        final var participation = optionalParticipation.get();
        if (dueDate != null && participation.getInitializationDate().isBefore(dueDate) && dueDate.isBefore(ZonedDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (textSubmission.isExampleSubmission() == Boolean.TRUE) {
            textSubmission = save(textSubmission);
        }
        else {
            textSubmission = save(textSubmission, participation);
        }
        return textSubmission;
    }

    /**
     * Saves the given submission. Is used for creating and updating text submissions. Rolls back if inserting fails - occurs for concurrent createTextSubmission() calls.
     *
     * @param textSubmission the submission that should be saved
     * @param participation  the participation the participation the submission belongs to
     * @return the textSubmission entity that was saved to the database
     */
    @Transactional(rollbackFor = Exception.class)
    public TextSubmission save(TextSubmission textSubmission, StudentParticipation participation) {
        // update submission properties
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);
        textSubmission.setParticipation(participation);
        textSubmission = textSubmissionRepository.save(textSubmission);

        participation.addSubmissions(textSubmission);

        if (textSubmission.isSubmitted()) {
            participation.setInitializationState(InitializationState.FINISHED);

            messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/exercise/" + participation.getExercise().getId() + "/participation",
                    participation);
        }
        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (textSubmission.getId() == null) {
            Optional<TextSubmission> optionalTextSubmission = savedParticipation.findLatestTextSubmission();
            if (optionalTextSubmission.isPresent()) {
                textSubmission = optionalTextSubmission.get();
            }
        }

        return textSubmission;
    }

    /**
     * The same as `save()`, but without participation, is used by example submission, which aren't linked to any participation
     *
     * @param textSubmission the submission to notifyCompass
     * @return the textSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public TextSubmission save(TextSubmission textSubmission) {
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);

        // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
        if (textSubmission.getResult() != null && textSubmission.getResult().getSubmission() == null) {
            textSubmission.getResult().setSubmission(textSubmission);
        }

        textSubmission = textSubmissionRepository.save(textSubmission);

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> getTextSubmissionWithoutManualResult(TextExercise textExercise) {
        if (textExercise.isAutomaticAssessmentEnabled() && textAssessmentQueueService.isPresent()) {
            return textAssessmentQueueService.get().getProposedTextSubmission(textExercise);
        }
        Random r = new Random();
        List<TextSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(textExercise.getId()).stream()
                .map(StudentParticipation::findLatestTextSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())));
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return List of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     *
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        return textSubmissionRepository.findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(exercise.getId()).stream()
                .filter(tS -> tS.getParticipation().findLatestSubmission().isPresent() && tS == tS.getParticipation().findLatestSubmission().get()).collect(Collectors.toList());
    }

    /**
     * Given an exercise id and a tutor id, it returns all the text submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param tutorId    - the id of the tutor we are interested in
     * @return a list of text Submissions
     */
    @Transactional(readOnly = true)
    public List<TextSubmission> getAllTextSubmissionsByTutorForExercise(Long exerciseId, Long tutorId) {
        // We take all the results in this exercise associated to the tutor, and from there we retrieve the submissions
        List<Result> results = this.resultRepository.findAllByParticipationExerciseIdAndAssessorId(exerciseId, tutorId);

        // TODO: properly load the submissions with all required data from the database without using @Transactional
        return results.stream().map(result -> {
            Submission submission = result.getSubmission();
            TextSubmission textSubmission = new TextSubmission();

            result.setSubmission(null);
            textSubmission.setLanguage(submission.getLanguage());
            textSubmission.setResult(result);
            textSubmission.setParticipation(submission.getParticipation());
            textSubmission.setId(submission.getId());
            textSubmission.setSubmissionDate(submission.getSubmissionDate());

            return textSubmission;
        }).collect(Collectors.toList());
    }

    /**
     * Given an exerciseId, returns all the submissions for that exercise, including their results. Submissions can be filtered to include only already submitted submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @return a list of text submissions for the given exercise id
     */
    public List<TextSubmission> getTextSubmissionsByExerciseId(Long exerciseId, boolean submittedOnly) {
        List<StudentParticipation> participations = studentParticipationRepository.findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(exerciseId);
        List<TextSubmission> textSubmissions = new ArrayList<>();

        for (StudentParticipation participation : participations) {
            Optional<TextSubmission> optionalTextSubmission = participation.findLatestTextSubmission();

            if (optionalTextSubmission.isEmpty()) {
                continue;
            }

            if (submittedOnly && optionalTextSubmission.get().isSubmitted() != Boolean.TRUE) {
                continue;
            }

            textSubmissions.add(optionalTextSubmission.get());
        }
        return textSubmissions;
    }

    public TextSubmission findOneWithEagerResultAndAssessor(Long submissionId) {
        return textSubmissionRepository.findByIdWithEagerResultAndAssessor(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + submissionId + "\" does not exist"));
    }

    public TextSubmission findOneWithEagerResultAndFeedback(Long submissionId) {
        return textSubmissionRepository.findByIdWithEagerResultAndFeedback(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * @param courseId the course we are interested in
     * @return the number of text submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    @Transactional(readOnly = true)
    public long countSubmissionsToAssessByCourseId(Long courseId) {
        return textSubmissionRepository.countByCourseIdSubmittedBeforeDueDate(courseId);
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of text submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    @Transactional(readOnly = true)
    public long countSubmissionsToAssessByExerciseId(Long exerciseId) {
        return textSubmissionRepository.countByExerciseIdSubmittedBeforeDueDate(exerciseId);
    }
}
