package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

class FeedbackServiceTest {

    @Test
    void createFeedbackFromTestCase() {
        FeedbackService f = new FeedbackService(null);

        String msg1 = """
                java.lang.AssertionError: Messsage1 with
                additional info
                on extra lines
                java.lang.AssertionError: Message2 on only a single line""";
        String msg2 = """
                org.opentest4j.AssertionFailedError: Message3 again
                with additions""";

        assertThat(f.createFeedbackFromTestCase("test1", List.of(msg1, msg2), false, ProgrammingLanguage.JAVA).getDetailText()).isEqualTo("""
                Messsage1 with
                additional info
                on extra lines

                Message2 on only a single line

                Message3 again
                with additions""");

        String msg3 = "Should not be changed";
        assertThat(f.createFeedbackFromTestCase("test2", List.of(msg3), false, ProgrammingLanguage.JAVA).getDetailText()).isEqualTo(msg3);
    }
}
