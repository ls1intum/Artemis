package de.tum.cit.aet.artemis.lecture.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
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
        if (unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf")) {
            pdfHash = sha256Hex(readAttachmentBytes(unit));
        }
        String videoSource = unit.getVideoSource() != null ? unit.getVideoSource() : "";
        String canonicalInput = pdfHash + "\n" + videoSource;
        return VERSION_PREFIX + sha256Hex(canonicalInput.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readAttachmentBytes(AttachmentVideoUnit unit) {
        Path path = FilePathConverter.fileSystemPathForExternalUri(URI.create(unit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT);
        try {
            return Files.readAllBytes(path);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot read attachment file for lecture unit " + unit.getId(), e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
