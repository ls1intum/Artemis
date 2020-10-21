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
                java.lang.AssertionError: expected:
                    4
                but was:
                    5""";
        String msg2 = """
                org.opentest4j.AssertionFailedError: Message2
                with additions""";
        String msg3 = """
                org.opentest4j.AssertionFailedError:\s
                message is only here on the next line:
                  expected:
                    []
                  to contain:
                    ["Java"]""";

        assertThat("""
                expected:
                    4
                but was:
                    5

                Message2
                with additions

                message is only here on the next line:
                  expected:
                    []
                  to contain:
                    ["Java"]""").isEqualTo(f.createFeedbackFromTestCase("test1", List.of(msg1, msg2, msg3), false, ProgrammingLanguage.JAVA).getDetailText());

        String msgMatchMultiple = """
                java.lang.AssertionError: expected:
                    4
                but was:
                    5
                org.opentest4j.AssertionFailedError: expected:
                    something else""";
        assertThat("""
                expected:
                    4
                but was:
                    5
                expected:
                    something else""").isEqualTo(f.createFeedbackFromTestCase("test2", List.of(msgMatchMultiple), false, ProgrammingLanguage.KOTLIN).getDetailText());

        String msgUnchanged = "Should not be changed";
        assertThat(msgUnchanged).isEqualTo(f.createFeedbackFromTestCase("test3", List.of(msgUnchanged), false, ProgrammingLanguage.JAVA).getDetailText());
    }
}
