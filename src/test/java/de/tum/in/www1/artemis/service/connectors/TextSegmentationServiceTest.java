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

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;
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

        TextSubmission submission0 = new TextSubmission(id).text(
                "Differences: \nAntipatterns: \n-Have one problem and two solutions(one problematic and one refactored)\n-Antipatterns are a sign of bad architecture and bad coding \nPattern:\n-Have one problem and one solution\n-Patterns are a sign of elaborated architecutre and coding");
        id++;
        textSubmissions.add(submission0);

        TextSubmission submission1 = new TextSubmission(id).text(
                "The main difference between patterns and antipatterns is, that patterns show you a good way to do something and antipatterns show a bad way to do something. Nevertheless patterns may become antipatterns in the course of changing understanding of how good software engineering looks like. One example for that is functional decomposition, which used to be a pattern and \"good practice\". Over the time it turned out that it is not a goog way to solve problems, so it became a antipattern.\n\nA pattern itsself is a proposed solution to a problem that occurs often and in different situations.\nIn contrast to that a antipattern shows commonly made mistakes when dealing with a certain problem. Nevertheless a refactored solution is aswell proposed.");
        textSubmissions.add(submission1);
        id++;

        TextSubmission submission2 = new TextSubmission(id).text(
                "1.Patterns can evolve into Antipatterns when change occurs\\n2. Pattern has one solution, whereas anti pattern can have subtypes of solution\\n3. Antipattern has negative consequences and symptom, where as patterns looks only into benefits and consequences");
        textSubmissions.add(submission2);
        id++;

        TextSubmission submission3 = new TextSubmission(id).text("Patterns: A way to Model code in differents ways \nAntipattern: A way of how Not to Model code");
        textSubmissions.add(submission3);
        id++;

        TextSubmission submission4 = new TextSubmission(id).text(
                "Antipatterns are used when there are common mistakes in software management and development to find these, while patterns by themselves are used to build software systems in the context of frequent change by reducing complexity and isolating the change.\nAnother difference is that the antipatterns have problematic solution and then refactored solution, while patterns only have a solution.");
        textSubmissions.add(submission4);
        id++;

        TextSubmission submission5 = new TextSubmission(id).text(
                "- In patterns we have a problem and a solution, in antipatterns we have a problematic solution and a refactored solution instead\n- patterns represent best practices from the industry etc. so proven concepts, whereas antipatterns shed a light on common mistakes during software development etc.");
        textSubmissions.add(submission5);
        id++;

        TextSubmission submission6 = new TextSubmission(id).text(
                "1) Patterns have one solution, antipatterns have to solutions (one problematic and one refactored).\n2) for the coice of patterns code has to be written; for antipatterns, the bad smell code already exists");
        textSubmissions.add(submission6);
        id++;

        TextSubmission submission7 = new TextSubmission(id).text(
                "Design Patterns:\n\nSolutions which are productive and efficient and are developed by Software Engineers over the years of practice and solving problems.\n\nAnti Patterns:\n\nKnown solutions which are actually bad or defective to certain kind of problems.");
        textSubmissions.add(submission7);
        id++;

        TextSubmission submission8 = new TextSubmission(id).text(
                "Patterns has one problem and one solution.\nAntipatterns have one problematic solution and a solution for that. The antipattern happens when  a solution that is been used for a long time can not apply anymore. ");
        textSubmissions.add(submission8);
        id++;

        TextSubmission submission9 = new TextSubmission(id).text(
                "Patterns identify problems and present solutions.\nAntipatterns identify problems but two kinds of solutions. One problematic solution and a better \"refactored\" version of the solution. Problematic solutions are suggested not to be used because they results in smells or hinder future work.");
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
