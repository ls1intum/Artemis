package de.tum.cit.aet.artemis.sharing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import jakarta.validation.constraints.NotNull;

import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.web.multipart.MultipartFile;

/**
 * just a utility class to hold the zip file from sharing
 */
@Profile("sharing")
public class SharingMultipartZipFile implements MultipartFile {

    private final String name;

    private final InputStream inputStream;

    public SharingMultipartZipFile(String name, InputStream inputStream) {
        this.name = name;
        this.inputStream = inputStream;
    }

    @Override
    @Nonnull
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
    @Nonnull
    public byte @NotNull [] getBytes() throws IOException {
        return this.inputStream.readAllBytes();
    }

    @Override
    @Nonnull
    public @NotNull InputStream getInputStream() throws IOException {
        return this.inputStream;
    }

    @Override
    public void transferTo(@NotNull File dest) throws IOException, IllegalStateException {
        FileUtils.copyInputStreamToFile(this.inputStream, dest);
    }
}
