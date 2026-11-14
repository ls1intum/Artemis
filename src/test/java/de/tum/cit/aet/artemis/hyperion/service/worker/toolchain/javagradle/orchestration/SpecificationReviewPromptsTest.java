package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecificationReviewPromptsTest {

    /** A probe that triggered the redesign is replayed only on the verified rebuilt exercise, so the prompt must not promise a check before statement authoring. */
    @Test
    void assessmentRedesignDoesNotPromiseAReplayBeforeStatementAuthoring() {
        String prompt = SpecificationReviewPrompts.assessmentRedesignPrompt("The graded suite accepts a main that only prints the expected output.");

        assertThat(prompt).doesNotContain("checked again before statement authoring").contains("reruns the executable assessment once the rebuilt exercise");
    }
}
