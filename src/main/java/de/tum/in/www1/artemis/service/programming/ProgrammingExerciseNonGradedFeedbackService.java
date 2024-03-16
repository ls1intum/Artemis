package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaFeedbackSuggestionsService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseNonGradedFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseNonGradedFeedbackService.class);

    public static final String NON_GRADED_FEEDBACK_SUGGESTION = "NonGradedFeedbackSuggestion:";

    private final GroupNotificationService groupNotificationService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final SubmissionService submissionService;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingMessagingService programmingMessagingService;

    public ProgrammingExerciseNonGradedFeedbackService(GroupNotificationService groupNotificationService,
            Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            SubmissionService submissionService, FeedbackRepository feedbackRepository, ResultService resultService, ParticipationService participationService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ResultRepository resultRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService1, ProgrammingMessagingService programmingMessagingService) {
        this.groupNotificationService = groupNotificationService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService.orElse(null);

        this.submissionService = submissionService;
        this.resultService = resultService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService1;
        this.programmingMessagingService = programmingMessagingService;
    }

    @Async
    public void handleNonGradedFeedbackRequest(Long exerciseId, ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        if (this.athenaFeedbackSuggestionsService != null) {
            generateAutomaticNonGradedFeedback(exerciseId, participation, programmingExercise);
        }
        else {
            log.debug("tutor is responsible to process feedback request: {}", exerciseId);
            setIndividualDueDateAndLockRepositoryAndInvalidatePreviousResults(participation, programmingExercise);
            groupNotificationService.notifyTutorGroupAboutNewFeedbackRequest(programmingExercise);
        }

    }

    private void generateAutomaticNonGradedFeedback(Long exerciseId, ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        log.debug("Using athena to generate feedback request: {}", exerciseId);

        // athena takes over the control here
        var submissionOptional = programmingExerciseParticipationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(participation.getId())
                .findLatestSubmission();
        if (submissionOptional.isEmpty()) {
            throw new BadRequestAlertException("No legal submissions found", "submission", "no submission");
        }
        var submission = submissionOptional.get();

        // save result and transmit it over websockets to notify the client about the status
        var automaticResult = this.submissionService.saveNewEmptyResult(submission);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC_AI);
        automaticResult.setRated(false);
        automaticResult.setScore(100.0); // requests allowed only if all autotests pass, => 100
        automaticResult.setSuccessful(null);
        automaticResult.setCompletionDate(ZonedDateTime.now().plusMinutes(5)); // we do not want to show dates without a completion date, but we want the students to know their
                                                                               // feedback request is in work
        automaticResult = this.resultRepository.save(automaticResult);

        try {

            setIndividualDueDateAndLockRepositoryAndInvalidatePreviousResults(participation, programmingExercise);
            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
            // now the client should be able to see new result

            log.debug("Submission id: {}", submission.getId());

            var athenaResponse = this.athenaFeedbackSuggestionsService.getProgrammingFeedbackSuggestions(programmingExercise, (ProgrammingSubmission) submission, false);

            var feedbacks = athenaResponse.stream().filter(individualFeedbackItem -> individualFeedbackItem.filePath() != null)
                    .filter(individualFeedbackItem -> individualFeedbackItem.description() != null).map(individualFeedbackItem -> {
                        var feedback = new Feedback();
                        String feedbackText;
                        if (Objects.nonNull(individualFeedbackItem.lineStart())) {
                            if (Objects.nonNull(individualFeedbackItem.lineEnd()) && !individualFeedbackItem.lineStart().equals(individualFeedbackItem.lineEnd())) {
                                feedbackText = String.format(NON_GRADED_FEEDBACK_SUGGESTION + "File %s at lines %d-%d", individualFeedbackItem.filePath(),
                                        individualFeedbackItem.lineStart(), individualFeedbackItem.lineEnd());
                            }
                            else {
                                feedbackText = String.format(NON_GRADED_FEEDBACK_SUGGESTION + "File %s at line %d", individualFeedbackItem.filePath(),
                                        individualFeedbackItem.lineStart());
                            }
                            feedback.setReference(String.format("file:%s_line:%d", individualFeedbackItem.filePath(), individualFeedbackItem.lineStart()));
                        }
                        else {
                            feedbackText = String.format(NON_GRADED_FEEDBACK_SUGGESTION + "File %s", individualFeedbackItem.filePath());
                        }
                        feedback.setText(feedbackText);
                        feedback.setDetailText(individualFeedbackItem.description());
                        feedback.setHasLongFeedbackText(false);
                        feedback.setType(FeedbackType.AUTOMATIC);
                        feedback.setCredits(0.0);
                        return feedback;
                    }).toList();

            automaticResult.setSuccessful(true);
            automaticResult.setCompletionDate(ZonedDateTime.now());

            this.resultService.storeFeedbackInResult(automaticResult, feedbacks, true);

            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
        }
        catch (Exception e) {
            log.error("Could not generate feedback");
            automaticResult.setSuccessful(false);
            automaticResult.setCompletionDate(ZonedDateTime.now());
            this.resultRepository.save(automaticResult);
            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
        }
        finally {
            programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation(participation);
            participation.setIndividualDueDate(null);
            this.programmingExerciseStudentParticipationRepository.save(participation);
        }
    }

    private void setIndividualDueDateAndLockRepositoryAndInvalidatePreviousResults(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        var currentDate = now();
        // The participations due date is a flag showing that a feedback request is sent
        participation.setIndividualDueDate(currentDate);

        participation = programmingExerciseStudentParticipationRepository.save(participation);
        programmingExerciseParticipationService.lockStudentRepositoryAndParticipation(programmingExercise, participation);
    }

}
