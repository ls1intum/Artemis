package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Simple wrapper around an {@link InputStream} representing a ZIP archive
 * received from the Sharing Platform.
 * <p>
 * Implements both {@link MultipartFile} and {@link Closeable} so it can be
 * handled like an uploaded file while ensuring proper stream cleanup.
 * </p>
 *
 * <h2>Usage</h2>
 * <ul>
 * <li>Provides a read-only view of the underlying ZIP file.</li>
 * <li>Supports standard {@link MultipartFile} operations such as {@code getBytes()},
 * {@code transferTo(File)}, and {@code getInputStream()}.</li>
 * <li>Implements {@link #close()} to release the input stream when done.</li>
 * </ul>
 *
 * <p>
 * Instances are typically short-lived and used within a try-with-resources block
 * when importing exercises from the Sharing Platform.
 * </p>
 */
public record SharingMultipartZipFile(@NotNull String name, @NotNull InputStream inputStream) implements MultipartFile, Closeable {

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getOriginalFilename() {
        return this.name;
    }

    @Override
    public String getContentType() {
        return "application/zip";
    }

    @Override
    public boolean isEmpty() {
        try {
            return this.inputStream.available() <= 0;
        }
        catch (IOException e) {
            return true; // unreadable
        }
    }

    @Override
    public long getSize() {
        try {
            return this.inputStream.available();
        }
        catch (IOException e) {
            return 0; // unreadable
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return this.inputStream.readAllBytes();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.inputStream;
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        FileUtils.copyInputStreamToFile(this.inputStream, dest);
    }

    /**
     * Closes the underlying input stream and releases any associated resources.
     */
    @Override
    public void close() throws IOException {
        if (this.inputStream != null) {
            inputStream.close();
        }
    }
}
