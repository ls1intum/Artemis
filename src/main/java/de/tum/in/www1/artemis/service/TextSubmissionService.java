package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class TextSubmissionService {

    private final TextSubmissionRepository textSubmissionRepository;
    private final ParticipationRepository participationRepository;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository,
                                 ParticipationRepository participationRepository,
                                 ParticipationService participationService,
                                 ResultRepository resultRepository) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.participationRepository = participationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
    }

    /**
     * Saves the given submission. Furthermore, the submission is added to the AutomaticSubmissionService,
     * if not submitted yet.
     * Is used for creating and updating text submissions.
     * If it is used for a submit action, Compass is notified about the new model.
     * Rolls back if inserting fails - occurs for concurrent createTextSubmission() calls.
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

        boolean isExampleSubmission = textSubmission.isExampleSubmission() == Boolean.TRUE;

        // Example submissions do not have a participation
        if (!isExampleSubmission) {
            textSubmission.setParticipation(participation);
        }
        textSubmission = textSubmissionRepository.save(textSubmission);

        // All the following are useful only for real submissions
        if (!isExampleSubmission) {
            participation.addSubmissions(textSubmission);

            User user = participation.getStudent();

            if (textSubmission.isSubmitted()) {
                participation.setInitializationState(InitializationState.FINISHED);
            } else if (textExercise.getDueDate() != null && !textExercise.isEnded()) {
                // save submission to HashMap if exercise not ended yet
                AutomaticSubmissionService.updateSubmission(textExercise.getId(), user.getLogin(), textSubmission);
            }
            Participation savedParticipation = participationRepository.save(participation);
            if (textSubmission.getId() == null) {
                textSubmission = savedParticipation.findLatestTextSubmission();
            }
        }

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any result.
     *
     * We relay for the randomness to `findAny()`, which return any element of the stream. While it is not
     * mathematically random, it is not deterministic
     * https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html#findAny--
     *
     * @param exerciseId the exercise we want to retrieve
     * @return a textSubmission without any result, if any
     */
    @Transactional(readOnly = true)
    public Optional<TextSubmission> textSubmissionWithoutResult(long exerciseId) {
        return this.participationService.findByExerciseIdWithEagerSubmissions(exerciseId)
            .stream()
            .peek(participation -> {
                participation.getExercise().setParticipations(null);
            })

            // Map to Latest Submission
            .map(Participation::findLatestTextSubmission)
            .filter(Objects::nonNull)

            // It needs to be submitted to be ready for assessment
            .filter(Submission::isSubmitted)

            .filter(textSubmission -> {
                Result result = resultRepository.findDistinctBySubmissionId(textSubmission.getId()).orElse(null);
                return result == null;

            })

            .findAny();
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

        return results.stream()
            .map(result -> {
                Submission submission = result.getSubmission();
                TextSubmission textSubmission = new TextSubmission();

                result.setSubmission(null);
                textSubmission.setResult(result);
                textSubmission.setParticipation(submission.getParticipation());
                textSubmission.setId(submission.getId());
                textSubmission.setSubmissionDate(submission.getSubmissionDate());

                return textSubmission;
            })
            .collect(Collectors.toList());
    }

    /**
     * Given a courseId, return the number of submissions for that course
     * @param courseId - the course we are interested in
     * @return a number of submissions for the course
     */
    public long countNumberOfSubmissions(Long courseId) {
        return textSubmissionRepository.countByParticipation_Exercise_Course_Id(courseId);
    }
}
