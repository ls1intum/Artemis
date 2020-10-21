package de.tum.in.www1.artemis.service.connectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.text.TextBlock;
import de.tum.in.www1.artemis.domain.text.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingError;

public class TextSegmentationServiceTest {

    private static final String SEGMENTATION_ENDPOINT = "http://localhost:8000/segment";

    @Test
    public void segmentSubmissions() throws NetworkingError {
        final TextSegmentationService segmentationService = new TextSegmentationService();
        ReflectionTestUtils.setField(segmentationService, "API_ENDPOINT", SEGMENTATION_ENDPOINT);

        // create 10 sample submissions with IDs from 0 to 9
        List<TextSubmission> textSubmissions = new ArrayList<>();
        long id = 0L;

        TextSubmission submission0 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_0);
        id++;
        textSubmissions.add(submission0);

        TextSubmission submission1 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_1);
        textSubmissions.add(submission1);
        id++;

        TextSubmission submission2 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_2);
        textSubmissions.add(submission2);
        id++;

        TextSubmission submission3 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_3);
        textSubmissions.add(submission3);
        id++;

        TextSubmission submission4 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_4);
        textSubmissions.add(submission4);
        id++;

        TextSubmission submission5 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_5);
        textSubmissions.add(submission5);
        id++;

        TextSubmission submission6 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_6);
        textSubmissions.add(submission6);
        id++;

        TextSubmission submission7 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_7);
        textSubmissions.add(submission7);
        id++;

        TextSubmission submission8 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_8);
        textSubmissions.add(submission8);
        id++;

        TextSubmission submission9 = new TextSubmission(id).text(TestSubmissionConstants.TEXT_9);
        textSubmissions.add(submission9);

        // calculate TextBlocks
        final List<TextBlock> textBlocks = segmentationService.segmentSubmissions(textSubmissions);

        assertThat(textBlocks, hasSize(33));

        int i = 0;
        for (TextSubmission submission : textSubmissions) {
            for (TextBlock textBlock : textBlocks) {
                if (submission.equals(textBlock.getSubmission())) {
                    i++;
                    // check if the TextBlock indices lie withing submission's text length
                    assertThat(textBlock.getStartIndex(), is(both(greaterThanOrEqualTo(0)).and(lessThanOrEqualTo(submission.getText().length()))));
                    assertThat(textBlock.getEndIndex(), is(both(greaterThanOrEqualTo(0)).and(lessThanOrEqualTo(submission.getText().length()))));
                    // check that textBlock contains the right text
                    assertThat(textBlock.getText(), equalTo(submission.getText().substring(textBlock.getStartIndex(), textBlock.getEndIndex())));
                    // check whether each submission has the right blocks assigned
                    assertThat(submission.getBlocks(), hasItem(equalTo(textBlock)));
                }
            }
        }

        // check that evey textBlock has the right submission set
        assertThat(i, equalTo(33));

    }

    @BeforeAll
    public static void runClassOnlyIfTextSegmentationIsAvailable() {
        assumeTrue(isTextSegmentationAvailable());
    }

    private static boolean isTextSegmentationAvailable() {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(SEGMENTATION_ENDPOINT).openConnection();
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
