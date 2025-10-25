package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * just a utility class to hold the zip file from sharing
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

    @Override
    public void close() throws IOException {
        if (this.inputStream != null) {
            inputStream.close();
        }
    }
}
