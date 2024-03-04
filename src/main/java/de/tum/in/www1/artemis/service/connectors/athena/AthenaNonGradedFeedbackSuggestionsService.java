package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.service.dto.athena.ExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.ProgrammingFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.SubmissionDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;

/**
 * Service for receiving non graded automatic feedback suggestions from the Athena service.
 */
@Service
@Profile("athena")
public class AthenaNonGradedFeedbackSuggestionsService {

    private static final Logger log = LoggerFactory.getLogger(AthenaNonGradedFeedbackSuggestionsService.class);

    private final AthenaConnector<RequestDTO, ResponseDTOText> textAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOProgramming> programmingAthenaConnector;

    private final AthenaModuleUrlHelper athenaModuleUrlHelper;

    private final AthenaDTOConverter athenaDTOConverter;

    /**
     * Creates a new AthenaNonGradedFeedbackSuggestionsService to receive non graded feedback suggestions from the Athena service.
     */
    public AthenaNonGradedFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleUrlHelper athenaModuleUrlHelper,
            AthenaDTOConverter athenaDTOConverter) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        this.athenaDTOConverter = athenaDTOConverter;
        this.athenaModuleUrlHelper = athenaModuleUrlHelper;
    }

    private record RequestDTO(ExerciseDTO exercise, SubmissionDTO submission) {
    }

    private record ResponseDTOText(List<TextFeedbackDTO> data) {
    }

    private record ResponseDTOProgramming(List<ProgrammingFeedbackDTO> data) {
    }

    /**
     * Calls the remote Athena service to get non graded feedback suggestions for a given submission.
     *
     * @param exercise   the {@link TextExercise} the suggestions are fetched for
     * @param submission the {@link TextSubmission} the suggestions are fetched for
     * @return a list of feedback suggestions
     */
    public List<TextFeedbackDTO> getTextFeedbackSuggestions(TextExercise exercise, TextSubmission submission) throws NetworkingException {
        throw new NotImplementedException("Not Implemented Yet");
    }

    /**
     * Calls the remote Athena service to get non graded feedback suggestions for a given programming submission.
     *
     * @param exercise   the {@link ProgrammingExercise} the suggestions are fetched for
     * @param submission the {@link ProgrammingSubmission} the suggestions are fetched for
     * @return a list of feedback suggestions
     */
    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(ProgrammingExercise exercise, ProgrammingSubmission submission) throws NetworkingException {
        log.debug("Start Athena Non Graded Feedback Suggestions Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise), athenaDTOConverter.ofSubmission(exercise.getId(), submission));
        ResponseDTOProgramming response = programmingAthenaConnector
                .invokeWithRetry(athenaModuleUrlHelper.getAthenaModuleUrl(exercise.getExerciseType()) + "/non_graded_feedback_suggestions", request, 0);
        log.info("Athena responded to non graded feedback suggestions request: {}", response.data);
        return response.data.stream().toList();
    }
}
