package de.tum.cit.aet.artemis.lecture.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

/**
 * Computes the content fingerprint of a lecture unit's ingestible source material.
 * <p>
 * The fingerprint covers exactly the inputs an ingestion run is derived from: the bytes of the PDF attachment and
 * the video source URL. It deliberately excludes names and visibility, which are propagated through the cheap
 * metadata and visibility webhooks and must not force a full re-ingestion. The fingerprint is sent to Iris with
 * every ingestion request, stamped verbatim into the vector store, and echoed back by the ingestion census, so
 * equality between this value and the stamp proves the index holds content derived from the unit's current sources.
 * <p>
 * The value is versioned with a {@code v1:} prefix so a future algorithm change re-certifies rows explicitly
 * instead of reporting them all as drifted.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class LectureUnitContentFingerprintService {

    private static final String VERSION_PREFIX = "v1:";

    /**
     * Compute the content fingerprint for the given unit's current source material.
     *
     * @param unit the attachment video unit
     * @return the versioned fingerprint string
     * @throws IllegalStateException if the attachment file exists in the database but cannot be read from disk
     */
    public String computeFingerprint(AttachmentVideoUnit unit) {
        String pdfHash = "";
        // Same rule as the ingestion payload, so the fingerprint certifies exactly the bytes Iris is sent: an external link has no stored file and counts as no PDF.
        if (unit.getAttachment() != null && unit.getAttachment().isStoredPdf()) {
            pdfHash = hashAttachment(unit);
        }
        String videoSource = unit.getVideoSource() != null ? unit.getVideoSource() : "";
        String canonicalInput = pdfHash + "\n" + videoSource;
        return VERSION_PREFIX + HexFormat.of().formatHex(sha256().digest(canonicalInput.getBytes(StandardCharsets.UTF_8)));
    }

    private String hashAttachment(AttachmentVideoUnit unit) {
        // Resolved like every other reader of the file, which also finds one still left in its lecture's directory.
        Path path = unit.getAttachment().fileLocation().map(FileSystemLocation::path)
                .orElseThrow(() -> new IllegalStateException("Attachment of lecture unit " + unit.getId() + " names no stored file"));
        // Streamed through the digest rather than read whole: the reconcile walk hashes every DONE PDF of a course on each pass.
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot read attachment file for lecture unit " + unit.getId(), e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
