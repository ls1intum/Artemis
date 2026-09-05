package de.tum.cit.aet.artemis.assessment.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A deduplicated, content-addressed feedback message text.
 * <p>
 * Programming-exercise feedback text is massively duplicated (identical test failure messages and static
 * code analysis messages repeat across students, builds, and courses). Instead of storing the text on
 * every {@link TestCaseFeedback} / {@link ScaFeedback} row, the text is stored once here, addressed by
 * its SHA-256 hash ({@code ux_feedback_message_hash} unique constraint).
 * <p>
 * Rows are <b>immutable</b>: a message is never edited in place — writers compute the hash of the new
 * text and re-point the referencing row ({@code FeedbackMessageService#getOrCreate}). The referencing
 * columns intentionally carry no database foreign key and no index (an index over ~34M referencing rows
 * would cost ~0.6 GB and no query filters by message); unreferenced messages are garbage-collected by the
 * admin orphan cleanup with a scan-based query. Both databases plan that scan as a single linear anti-join
 * pass, so the missing index costs one sequential scan of each referencing table per cleanup run: measured
 * at production scale (615k messages, 31.7M test-case and 2.5M SCA rows, 130k collected) at 2.8 s on
 * PostgreSQL 18 and 15 s on MySQL 9.
 */
@Entity
@Table(name = "feedback_message")
@BatchSize(size = 50)
public class FeedbackMessage extends DomainObject {

    @Column(name = "hash", nullable = false)
    private byte[] hash;

    @Column(name = "text", nullable = false)
    private String text;

    /**
     * Garbage-collection grace timestamp: set on creation and refreshed whenever an existing row is reused
     * ({@code FeedbackMessageService#getOrCreate}). A message row is committed before the feedback rows
     * that reference it (there is no surrounding transaction), so the cleanup only deletes unreferenced
     * messages whose timestamp is older than a grace period — the refresh-on-reuse extends that protection
     * to old rows that are being re-referenced right now.
     */
    @Column(name = "created_date", nullable = false)
    private ZonedDateTime createdDate = ZonedDateTime.now();

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /**
     * Computes the content address of a message text: the SHA-256 hash of its UTF-8 bytes. This must stay
     * in sync with the hashing used by the Liquibase backfill, which hashes explicitly UTF-8-encoded bytes
     * regardless of the column charset (MySQL {@code UNHEX(SHA2(CONVERT(text USING utf8mb4), 256))},
     * PostgreSQL {@code sha256(convert_to(text, 'UTF8'))}).
     *
     * @param text the message text, never null
     * @return the 32-byte SHA-256 hash of the text
     */
    public static byte[] hashOf(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return "FeedbackMessage{id=" + getId() + ", textLength=" + (text == null ? 0 : text.length()) + '}';
    }
}
