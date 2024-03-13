package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
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

    private final GroupNotificationService groupNotificationService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final SubmissionService submissionService;

    private final ResultService resultService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public ProgrammingExerciseNonGradedFeedbackService(GroupNotificationService groupNotificationService,
            Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            SubmissionService submissionService, FeedbackRepository feedbackRepository, ResultService resultService, ParticipationService participationService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService1) {
        this.groupNotificationService = groupNotificationService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService.orElse(null);

        this.submissionService = submissionService;
        this.resultService = resultService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService1;
    }

    @Async
    public void handleNonGradedFeedbackRequest(Long exerciseId, ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        if (this.athenaFeedbackSuggestionsService != null) {
            generateAutomaticNonGradedFeedback(exerciseId, participation, programmingExercise);
        }
        else {
            log.debug("tutor is responsible to process feedback request: {}", exerciseId);
            groupNotificationService.notifyTutorGroupAboutNewFeedbackRequest(programmingExercise);
        }

    }

    private void generateAutomaticNonGradedFeedback(Long exerciseId, ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        log.debug("Using athena to generate feedback request: {}", exerciseId);
        try {

            // athena takes over the control here
            var submissionOptional = programmingExerciseParticipationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(participation.getId())
                    .findLatestSubmission();
            if (submissionOptional.isEmpty()) {
                throw new BadRequestAlertException("No legal submissions found", "submission", "no submission");
            }
            var submission = submissionOptional.get();
            log.debug("Submission id: ", submission.getId());

            var athenaResponse = this.athenaFeedbackSuggestionsService.getProgrammingFeedbackSuggestions(programmingExercise, (ProgrammingSubmission) submission, false);
            var automaticResult = this.submissionService.saveNewEmptyResult(submission);

            var feedbacks = athenaResponse.stream().map(individualFeedbackItem -> {
                var feedback = new Feedback();
                // todo proper formatting for single line, multilines, no lines etc
                feedback.setText(String.format("NonGradedFeedbackSuggestion:File %s at lines %d-%d", individualFeedbackItem.filePath(), individualFeedbackItem.lineStart(),
                        individualFeedbackItem.lineEnd()));
                feedback.setDetailText(individualFeedbackItem.description());
                feedback.setHasLongFeedbackText(false);
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setReference(String.format("file:%s_line:%d", individualFeedbackItem.filePath(), individualFeedbackItem.lineStart()));
                feedback.setCredits(0.0);
                return feedback;
            }).toList();

            automaticResult.setAssessmentType(AssessmentType.AUTOMATIC);
            automaticResult.setRated(false);
            automaticResult.setSuccessful(true);
            automaticResult.setTestCaseCount(-1);
            automaticResult.setCompletionDate(ZonedDateTime.now());

            this.resultService.storeFeedbackInResult(automaticResult, feedbacks, true);

            this.resultService.notifyAboutNewResult(automaticResult);
        }
        catch (NetworkingException e) {
            log.error("Athena could not be reached");
        }
        finally {
            programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation(participation);
            participation.setIndividualDueDate(null);
            this.programmingExerciseStudentParticipationRepository.save(participation);
        }
    }

}
