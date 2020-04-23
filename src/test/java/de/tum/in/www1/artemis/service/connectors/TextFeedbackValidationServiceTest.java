package de.tum.in.www1.artemis.service.connectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.exception.NetworkingError;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TextFeedbackValidationServiceTest {

    private static String VALIDATION_ENDPOINT = "http://localhost:8080/validate";

    @Test
    public void validateFeedbackTest() throws NetworkingError {
        final TextFeedbackValidationService validationService = new TextFeedbackValidationService();
        ReflectionTestUtils.setField(validationService, "API_ENDPOINT", VALIDATION_ENDPOINT);

        TextBlock candidate = new TextBlock();
            candidate.text("Adaptive Development improves the reaction to changing customer needs (react to changing requirements)");

            List<TextBlock> references = new ArrayList<>();

            TextBlock firstReference = new TextBlock();
            firstReference.setText("Adaptive development refers to development that adapts itself to changing requirements.");
            references.add(firstReference);

            TextBlock secondReference = new TextBlock();
            secondReference.setText("adaptive development is to react to changing requirements; it improves the reaction to changing customer needs.");
            references.add(secondReference);

            TextBlock thirdReference = new TextBlock();
            thirdReference.setText("Adaptive means to react to changing requirements, therefore adaptive development improves the reaction to changing customer needs.");
            references.add(thirdReference);

            double confidence = validationService.validateFeedback(candidate, references);
            assertThat(confidence, greaterThanOrEqualTo(0.0));
            assertThat(confidence, lessThanOrEqualTo(100.0));
    }


    @BeforeAll
    public static void runClassOnlyIfValidationServiceAvailable() {
        assumeTrue(isValidationAvailable());
    }

    private static boolean isValidationAvailable() {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(VALIDATION_ENDPOINT).openConnection();
            httpURLConnection.setRequestMethod("HEAD");
            httpURLConnection.setConnectTimeout(1000);

            final int responseCode = httpURLConnection.getResponseCode();

            return (responseCode == 405);
        }
        catch (IOException e) {
            return false;
        }
    }
}
