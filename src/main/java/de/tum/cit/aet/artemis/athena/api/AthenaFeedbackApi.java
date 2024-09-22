package de.tum.cit.aet.artemis.athena.api;

import java.util.List;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSendingService;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Controller
public class AthenaFeedbackApi extends AbstractAthenaApi {

    private final Optional<AthenaFeedbackSuggestionsService> optionalAthenaFeedbackSuggestionsService;

    private final Optional<AthenaFeedbackSendingService> optionalAthenaFeedbackSendingService;

    public AthenaFeedbackApi(Environment environment, Optional<AthenaFeedbackSuggestionsService> optionalAthenaFeedbackSuggestionsService,
            Optional<AthenaFeedbackSendingService> optionalAthenaFeedbackSendingService) {
        super(environment);
        this.optionalAthenaFeedbackSuggestionsService = optionalAthenaFeedbackSuggestionsService;
        this.optionalAthenaFeedbackSendingService = optionalAthenaFeedbackSendingService;
    }

    public void sendFeedback(Exercise exercise, Submission submission, List<Feedback> feedbacks) {
        optionalAthenaFeedbackSendingService.ifPresent(afs -> afs.sendFeedback(exercise, submission, feedbacks));
    }

    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(ProgrammingExercise exercise, ProgrammingSubmission submission, boolean isGraded)
            throws NetworkingException {
        return getOrThrow(optionalAthenaFeedbackSuggestionsService).getProgrammingFeedbackSuggestions(exercise, submission, isGraded);
    }

    public List<TextFeedbackDTO> getTextFeedbackSuggestions(TextExercise exercise, TextSubmission submission, boolean isGraded) throws NetworkingException {
        return getOrThrow(optionalAthenaFeedbackSuggestionsService).getTextFeedbackSuggestions(exercise, submission, isGraded);
    }
}
