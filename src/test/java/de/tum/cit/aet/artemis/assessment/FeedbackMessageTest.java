package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackMessage;

class FeedbackMessageTest {

    /**
     * The content address must be the SHA-256 of the UTF-8 bytes of the text, and nothing else: the Liquibase
     * backfill computes it in SQL ({@code UNHEX(SHA2(CONVERT(text USING utf8mb4), 256))} respectively
     * {@code sha256(convert_to(text, 'UTF8'))}), so a message migrated from the old feedback table and the same
     * message written by a new build have to end up on the same row. A text outside ASCII is what tells the two
     * encodings apart.
     */
    @Test
    void hashesTheUtf8BytesOfTheText() throws NoSuchAlgorithmException {
        final String text = "Erwartet 'Prüfung', war 'Prufung' — größer als 4 Zeichen";

        final byte[] hash = FeedbackMessage.hashOf(text);

        assertThat(hash).hasSize(32).isEqualTo(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void hashesEqualTextsEquallyAndDifferentTextsDifferently() {
        assertThat(FeedbackMessage.hashOf("same text")).isEqualTo(FeedbackMessage.hashOf("same text")).isNotEqualTo(FeedbackMessage.hashOf("same text "));
    }

    @Test
    void keepsTheValuesItIsGiven() {
        final FeedbackMessage message = new FeedbackMessage();
        final byte[] hash = FeedbackMessage.hashOf("stored text");
        message.setHash(hash);
        message.setText("stored text");

        assertThat(message.getHash()).isEqualTo(hash);
        assertThat(message.getText()).isEqualTo("stored text");
        // set on construction, so that a row is never written without the garbage-collection grace timestamp
        assertThat(message.getCreatedDate()).isBefore(ZonedDateTime.now().plusMinutes(1)).isAfter(ZonedDateTime.now().minusMinutes(1));
    }

    /**
     * The message text can be up to 20k characters, which has no place in a log line.
     */
    @Test
    void doesNotPutTheTextIntoItsStringRepresentation() {
        final FeedbackMessage message = new FeedbackMessage();
        message.setText("a very long and very secret failure message");

        assertThat(message).hasToString("FeedbackMessage{id=null, textLength=43}");
    }
}
