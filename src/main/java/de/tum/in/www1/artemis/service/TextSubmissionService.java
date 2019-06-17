package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;

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
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class TextSubmissionService {

    private final TextSubmissionRepository textSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ParticipationRepository participationRepository;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    private final UserService userService;

    private final SimpMessageSendingOperations messagingTemplate;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, SubmissionRepository submissionRepository, ParticipationRepository participationRepository,
            ParticipationService participationService, ResultRepository resultRepository, UserService userService, SimpMessageSendingOperations messagingTemplate) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.participationRepository = participationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    public void checkSubmissionLockLimit(long courseId) {
        long numberOfLockedSubmissions = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userService.getUserWithGroupsAndAuthorities().getId(), courseId);
        if (numberOfLockedSubmissions >= MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) {
            throw new BadRequestAlertException("The limit of locked submissions has been reached", "submission", "lockedSubmissionsLimitReached");
        }
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
        if (textSubmission.isExampleSubmission() == Boolean.TRUE) {
            textSubmission = save(textSubmission);
        }
        else {
            Optional<Participation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginAnyState(textExercise.getId(), principal.getName());
            if (!optionalParticipation.isPresent()) {
                throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + textExercise.getId());
            }
            Participation participation = optionalParticipation.get();

            if (participation.getInitializationState() == InitializationState.FINISHED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot submit more than once");
            }

            textSubmission = save(textSubmission, textExercise, participation);
        }
        return textSubmission;
    }

    /**
     * Saves the given submission. Furthermore, the submission is added to the AutomaticSubmissionService, if not submitted yet. Is used for creating and updating text submissions.
     * If it is used for a submit action, Compass is notified about the new model. Rolls back if inserting fails - occurs for concurrent createTextSubmission() calls.
     *
     * @param textSubmission the submission to notifyCompass
     * @param textExercise   the exercise to notifyCompass in
     * @param participation  the participation
     * @return the textSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public TextSubmission save(TextSubmission textSubmission, TextExercise textExercise, Participation participation) {
        // update submission properties
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);
        textSubmission.setParticipation(participation);
        textSubmission = textSubmissionRepository.save(textSubmission);

        participation.addSubmissions(textSubmission);

        User user = participation.getStudent();

        if (textSubmission.isSubmitted()) {
            participation.setInitializationState(InitializationState.FINISHED);

            messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/exercise/" + participation.getExercise().getId() + "/participation",
                    participation);
        }
        Participation savedParticipation = participationRepository.save(participation);
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
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any result.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without result
     * @return a textSubmission without any result, if any
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> getTextSubmissionWithoutResult(TextExercise textExercise) {
        Random r = new Random();
        List<TextSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(textExercise.getId()).stream()
                .map(Participation::findLatestTextSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())));
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

        return results.stream().map(result -> {
            Submission submission = result.getSubmission();
            TextSubmission textSubmission = new TextSubmission();

            result.setSubmission(null);
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
        List<Participation> participations = participationRepository.findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(exerciseId);
        List<TextSubmission> textSubmissions = new ArrayList<>();

        for (Participation participation : participations) {
            Optional<TextSubmission> optionalTextSubmission = participation.findLatestTextSubmission();

            if (!optionalTextSubmission.isPresent()) {
                continue;
            }

            if (submittedOnly && optionalTextSubmission.get().isSubmitted() != Boolean.TRUE) {
                continue;
            }

            if (optionalTextSubmission.get().getResult() != null) {
                optionalTextSubmission.get().getResult().getAssessor().setGroups(null);
            }

            textSubmissions.add(optionalTextSubmission.get());
        }
        return textSubmissions;
    }

    public TextSubmission findOneWithEagerResultAndAssessor(Long id) {
        return textSubmissionRepository.findByIdWithEagerResultAndAssessor(id)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + id + "\" does not exist"));
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
