package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class TextSubmissionService extends SubmissionService {

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextClusterRepository textClusterRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ParticipationService participationService;

    private final Optional<TextAssessmentQueueService> textAssessmentQueueService;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, TextClusterRepository textClusterRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository, UserService userService,
            Optional<TextAssessmentQueueService> textAssessmentQueueService, AuthorizationCheckService authCheckService) {
        super(submissionRepository, userService, authCheckService, resultRepository);
        this.textSubmissionRepository = textSubmissionRepository;
        this.textClusterRepository = textClusterRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
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
        final var optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(textExercise, principal.getName());
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
        }
        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (textSubmission.getId() == null) {
            Optional<Submission> optionalTextSubmission = savedParticipation.findLatestSubmission();
            if (optionalTextSubmission.isPresent()) {
                textSubmission = (TextSubmission) optionalTextSubmission.get();
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
    public Optional<TextSubmission> getTextSubmissionWithoutManualResult(TextExercise textExercise) {
        return getTextSubmissionWithoutManualResult(textExercise, false);
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue skip using the assessment queue and do NOT optimize the assessment order (default: false)
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> getTextSubmissionWithoutManualResult(TextExercise textExercise, boolean skipAssessmentQueue) {
        if (textExercise.isAutomaticAssessmentEnabled() && textAssessmentQueueService.isPresent() && !skipAssessmentQueue) {
            return textAssessmentQueueService.get().getProposedTextSubmission(textExercise);
        }

        Random random = new Random();
        var participations = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(textExercise.getId());
        var submissionsWithoutResult = participations.stream().map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).collect(toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        var submissionWithoutResult = (TextSubmission) submissionsWithoutResult.get(random.nextInt(submissionsWithoutResult.size()));
        return Optional.of(submissionWithoutResult);
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return List of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     *
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        final List<TextSubmission> submissions = textSubmissionRepository.findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(exercise.getId());

        final Set<Long> clusterIds = submissions.stream().flatMap(submission -> submission.getBlocks().stream()).map(TextBlock::getCluster).filter(Objects::nonNull)
                .map(TextCluster::getId).collect(toSet());

        // To prevent lazy loading many elements later on, we fetch all clusters with text blocks here.
        final Map<Long, TextCluster> textClusterMap = textClusterRepository.findAllByIdsWithEagerTextBlocks(clusterIds).stream()
                .collect(toMap(TextCluster::getId, textCluster -> textCluster));

        // link up clusters with eager blocks
        submissions.stream().flatMap(submission -> submission.getBlocks().stream()).forEach(textBlock -> {
            if (textBlock.getCluster() != null) {
                textBlock.setCluster(textClusterMap.get(textBlock.getCluster().getId()));
            }
        });

        return submissions.stream().filter(tS -> tS.getParticipation().findLatestSubmission().isPresent() && tS == tS.getParticipation().findLatestSubmission().get())
                .collect(toList());
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
        }).collect(toList());
    }

    /**
     * Given an exerciseId, returns all the submissions for that exercise, including their results. Submissions can be filtered to include only already submitted submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @return a list of text submissions for the given exercise id
     */
    public List<TextSubmission> getTextSubmissionsByExerciseId(Long exerciseId, boolean submittedOnly) {
        List<StudentParticipation> participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exerciseId);
        List<TextSubmission> textSubmissions = new ArrayList<>();

        for (StudentParticipation participation : participations) {
            Optional<Submission> optionalTextSubmission = participation.findLatestSubmission();

            if (optionalTextSubmission.isEmpty()) {
                continue;
            }

            if (submittedOnly && optionalTextSubmission.get().isSubmitted() != Boolean.TRUE) {
                continue;
            }

            textSubmissions.add((TextSubmission) optionalTextSubmission.get());
        }
        return textSubmissions;
    }

    /**
     * Find a text submission of the given exercise that still needs to be assessed and lock it to prevent other tutors from receiving and assessing it.
     *
     * @param textExercise the exercise the submission should belong to
     * @return a locked modeling submission that needs an assessment
     */
    public TextSubmission findAndLockTextSubmissionToBeAssessed(TextExercise textExercise) {
        TextSubmission textSubmission = getTextSubmissionWithoutManualResult(textExercise)
                .orElseThrow(() -> new EntityNotFoundException("Text submission for exercise " + textExercise.getId() + " could not be found"));
        lockSubmission(textSubmission);
        return textSubmission;
    }

    public TextSubmission findOneWithEagerResultAndAssessor(Long submissionId) {
        return textSubmissionRepository.findByIdWithEagerResultAndAssessor(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + submissionId + "\" does not exist"));
    }

    public TextSubmission findOneWithEagerResultAndFeedback(Long submissionId) {
        return textSubmissionRepository.findByIdWithEagerResultAndFeedback(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + submissionId + "\" does not exist"));
    }
}
