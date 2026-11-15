package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.communication.domain.Faq;

class FaqContentFingerprintTest {

    @Test
    void shouldBeStableAndVersioned() {
        assertThat(Faq.contentFingerprint("Q", "A")).startsWith("v1:").isEqualTo(Faq.contentFingerprint("Q", "A"));
    }

    /** The reconcile reads title and answer through a projection, the push through the entity; both must give the same value, also for null. */
    @Test
    void shouldTreatNullAsEmptyAndMatchTheEntity() {
        Faq faq = new Faq();
        faq.setQuestionTitle("Q");
        faq.setQuestionAnswer(null);

        assertThat(faq.computeContentFingerprint()).isEqualTo(Faq.contentFingerprint("Q", "")).isEqualTo(Faq.contentFingerprint("Q", null));
    }

    @Test
    void shouldNotCollideWhenTextMovesBetweenTitleAndAnswer() {
        assertThat(Faq.contentFingerprint("ab", "c")).isNotEqualTo(Faq.contentFingerprint("a", "bc"));
        assertThat(Faq.contentFingerprint("Q", "A")).isNotEqualTo(Faq.contentFingerprint("Q", "B"));
    }
}
