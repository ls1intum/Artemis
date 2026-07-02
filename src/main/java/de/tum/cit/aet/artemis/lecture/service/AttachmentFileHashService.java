package de.tum.cit.aet.artemis.lecture.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class AttachmentFileHashService {

    private static final String SHA_256 = "SHA-256";

    private static final int BUFFER_SIZE = 8192;

    public FileHash hash(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return hash(inputStream);
        }
        catch (IOException e) {
            throw new AttachmentFileHashException("Could not calculate attachment file hash", e);
        }
    }

    public FileHash hash(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return hash(inputStream);
        }
        catch (IOException e) {
            throw new AttachmentFileHashException("Could not calculate attachment file hash", e);
        }
    }

    private FileHash hash(InputStream inputStream) {
        MessageDigest messageDigest = createMessageDigest();
        byte[] buffer = new byte[BUFFER_SIZE];

        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest)) {
            while (digestInputStream.read(buffer) != -1) {
                // Read the stream fully so DigestInputStream can update the digest incrementally.
            }
        }
        catch (IOException e) {
            throw new AttachmentFileHashException("Could not calculate attachment file hash", e);
        }

        return new FileHash(SHA_256, HexFormat.of().formatHex(messageDigest.digest()));
    }

    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        }
        catch (NoSuchAlgorithmException e) {
            throw new AttachmentFileHashException("SHA-256 message digest is not available", e);
        }
    }

    public record FileHash(String algorithm, String value) {
    }
}
