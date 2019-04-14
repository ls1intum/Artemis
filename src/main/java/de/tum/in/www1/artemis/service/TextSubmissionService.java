package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;

@Service
@Transactional
public class TextSubmissionService {

    private final TextSubmissionRepository textSubmissionRepository;

    private final ParticipationRepository participationRepository;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, ParticipationRepository participationRepository, ParticipationService participationService,
            ResultRepository resultRepository) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.participationRepository = participationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
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
        }
        else if (textExercise.getDueDate() != null && !textExercise.isEnded()) {
            // save submission to HashMap if exercise not ended yet
            AutomaticSubmissionService.updateSubmission(textExercise.getId(), user.getLogin(), textSubmission);
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
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any result. We relay for the randomness to `findAny()`, which return any
     * element of the stream. While it is not mathematically random, it is not deterministic https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html#findAny--
     *
     * @param exerciseId the exercise we want to retrieve
     * @return a textSubmission without any result, if any
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> textSubmissionWithoutResult(long exerciseId) {
        return this.participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(exerciseId).stream()
                .peek(participation -> participation.getExercise().setParticipations(null))
                // Map to Latest Submission
                .map(Participation::findLatestTextSubmission).filter(Optional::isPresent).map(Optional::get).findAny();
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
}
