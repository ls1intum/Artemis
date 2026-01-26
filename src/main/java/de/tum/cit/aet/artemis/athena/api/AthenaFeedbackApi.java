package de.tum.cit.aet.artemis.athena.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSendingService;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_ATHENA)
@Controller
@Lazy
public class AthenaFeedbackApi extends AbstractAthenaApi {

    private final AthenaFeedbackSendingService athenaFeedbackSendingService;

    private final AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    public AthenaFeedbackApi(AthenaFeedbackSendingService athenaFeedbackSendingService, AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService) {
        this.athenaFeedbackSendingService = athenaFeedbackSendingService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
    }

    public List<TextFeedbackDTO> getTextFeedbackSuggestions(TextExercise exercise, TextSubmission submission, boolean isGraded) throws NetworkingException {
        return athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(exercise, submission, isGraded);
    }

    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(ProgrammingExercise exercise, ProgrammingSubmission submission, boolean isGraded)
            throws NetworkingException {
        return athenaFeedbackSuggestionsService.getProgrammingFeedbackSuggestions(exercise, submission, isGraded);
    }

    public List<ModelingFeedbackDTO> getModelingFeedbackSuggestions(ModelingExercise exercise, ModelingSubmission submission, boolean isGraded) throws NetworkingException {
        return athenaFeedbackSuggestionsService.getModelingFeedbackSuggestions(exercise, submission, isGraded);
    }

    public void checkRateLimitOrThrow(StudentParticipation participation) {
        athenaFeedbackSuggestionsService.checkRateLimitOrThrow(participation);
    }

    public void checkLatestSubmissionHasNoAthenaResultOrThrow(Submission submission) {
        athenaFeedbackSuggestionsService.checkLatestSubmissionHasNoAthenaResultOrThrow(submission);
    }

    public void sendFeedback(Exercise exercise, Submission submission, Collection<Feedback> feedbacks) {
        athenaFeedbackSendingService.sendFeedback(exercise, submission, feedbacks);
    }
}
