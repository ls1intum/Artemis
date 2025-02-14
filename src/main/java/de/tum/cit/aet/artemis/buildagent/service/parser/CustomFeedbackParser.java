package de.tum.cit.aet.artemis.buildagent.service.parser;

import java.io.IOException;
import java.util.InvalidPropertiesFormatException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.buildagent.dto.CustomFeedback;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;

public final class CustomFeedbackParser {

    private static final Logger log = LoggerFactory.getLogger(CustomFeedbackParser.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private CustomFeedbackParser() {

    }

    /**
     * Parses the test result file and extracts failed and successful tests.
     *
     * @param fileName             The name of the result file. Needs to be present.
     * @param testResultFileString The content of the test result file as a String.
     * @param failedTests          A list of failed tests. This list will be populated by the method.
     * @param successfulTests      A list of successful tests. This list will be populated by the method.
     */
    public static void processTestResultFile(final String fileName, final String testResultFileString, final List<LocalCITestJobDTO> failedTests,
            final List<LocalCITestJobDTO> successfulTests) {
        final CustomFeedback feedback;
        try {
            feedback = mapper.readValue(testResultFileString, CustomFeedback.class);
            validateCustomFeedback(fileName, feedback);
        }
        catch (IOException e) {
            log.error("Error during custom Feedback creation. {}", e.getMessage(), e);
            return;
        }
        List<LocalCITestJobDTO> toAddFeedbackTo = feedback.successful() ? successfulTests : failedTests;
        toAddFeedbackTo.add(new LocalCITestJobDTO(feedback.name(), List.of(feedback.getMessage())));
    }

    /**
     * Checks that the custom feedback has a valid format
     * <p>
     * A custom feedback has to have a non-empty, non only-whitespace name to be able to identify it in Artemis.
     * If it is not successful, there has to be a message explaining a reason why this is the case.
     *
     * @param fileName where the custom feedback was read from.
     * @param feedback the custom feedback to validate.
     * @throws InvalidPropertiesFormatException if one of the invariants described above does not hold.
     */
    private static void validateCustomFeedback(final String fileName, final CustomFeedback feedback) throws InvalidPropertiesFormatException {
        if (feedback.name() == null || feedback.name().trim().isEmpty()) {
            throw new InvalidPropertiesFormatException(String.format("Custom feedback from file %s needs to have a name attribute.", fileName));
        }
        if (!feedback.successful() && (feedback.message() == null || feedback.message().trim().isEmpty())) {
            throw new InvalidPropertiesFormatException(String.format("Custom non-success feedback from file %s needs to have a message", fileName));
        }
    }
}
