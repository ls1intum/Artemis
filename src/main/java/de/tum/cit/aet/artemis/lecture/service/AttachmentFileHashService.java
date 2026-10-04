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

    /**
     * Calculates the SHA-256 hash of an uploaded attachment file.
     *
     * @param file the uploaded file to hash
     * @return the calculated SHA-256 hash
     */
    public FileHash sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return sha256(inputStream);
        }
        catch (IOException e) {
            throw new AttachmentFileHashException("Could not hash uploaded attachment file", e);
        }
    }

    /**
     * Calculates the SHA-256 hash of a stored attachment file.
     *
     * @param path the path of the stored file to hash
     * @return the calculated SHA-256 hash
     */
    public FileHash sha256(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return sha256(inputStream);
        }
        catch (IOException e) {
            throw new AttachmentFileHashException("Could not hash stored attachment file", e);
        }
    }

    private FileHash sha256(InputStream inputStream) {
        MessageDigest messageDigest = createMessageDigest();
        byte[] buffer = new byte[BUFFER_SIZE];

        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest)) {
            while (digestInputStream.read(buffer) != -1) {
                // Read the stream fully so DigestInputStream can update the digest incrementally.
            }
        }
        catch (IOException e) {
            throw new AttachmentFileHashException("Could not hash attachment file stream", e);
        }

        return new FileHash(SHA_256, HexFormat.of().formatHex(messageDigest.digest()));
    }

    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        }
        catch (NoSuchAlgorithmException e) {
            throw new AttachmentFileHashException("Could not initialize SHA-256 digest", e);
        }
    }

    public record FileHash(String algorithm, String value) {
    }
}
