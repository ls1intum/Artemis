package de.tum.cit.aet.artemis.core.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link MultipartFile} implementation for Apache Commons FileUpload.
 *
 * @author Trevor D. Cook
 * @author Juergen Hoeller
 * @since 29.09.2003
 */
@SuppressWarnings("serial")
public class CommonsMultipartFile implements MultipartFile, Serializable {

    private final FileItem fileItem;

    private final long size;

    /**
     * Create an instance wrapping the given FileItem.
     *
     * @param fileItem the FileItem to wrap
     */
    public CommonsMultipartFile(FileItem fileItem) {
        this.fileItem = fileItem;
        this.size = this.fileItem.getSize();
    }

    @Override
    public @NotNull String getName() {
        return this.fileItem.getFieldName();
    }

    @Override
    public String getOriginalFilename() {
        String filename = this.fileItem.getName();
        if (filename == null) {
            // Should never happen.
            return "";
        }

        // Check for Unix-style path
        int unixSep = filename.lastIndexOf('/');
        // Check for Windows-style path
        int winSep = filename.lastIndexOf('\\');
        // Cut off at latest possible point
        int pos = Math.max(winSep, unixSep);
        if (pos != -1) {
            // Any sort of path separator found...
            return filename.substring(pos + 1);
        }
        else {
            // A plain name
            return filename;
        }
    }

    @Override
    public String getContentType() {
        return this.fileItem.getContentType();
    }

    @Override
    public boolean isEmpty() {
        return (this.size == 0);
    }

    @Override
    public long getSize() {
        return this.size;
    }

    @Override
    public byte[] getBytes() {
        if (!isAvailable()) {
            throw new IllegalStateException("File has been moved - cannot be read again");
        }
        byte[] bytes = this.fileItem.get();
        return (bytes != null ? bytes : new byte[0]);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("File has been moved - cannot be read again");
        }
        InputStream inputStream = this.fileItem.getInputStream();
        return (inputStream != null ? inputStream : InputStream.nullInputStream());
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        if (!isAvailable()) {
            throw new IllegalStateException("File has already been moved - cannot be transferred again");
        }

        if (dest.exists() && !dest.delete()) {
            throw new IOException("Destination file [" + dest.getAbsolutePath() + "] already exists and could not be deleted");
        }

        try {
            this.fileItem.write(dest);
        }
        catch (FileUploadException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        catch (IllegalStateException | IOException ex) {
            // Pass through IllegalStateException when coming from FileItem directly,
            // or propagate an exception from I/O operations within FileItem.write
            throw ex;
        }
        catch (Exception ex) {
            throw new IOException("File transfer failed", ex);
        }
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        if (!isAvailable()) {
            throw new IllegalStateException("File has already been moved - cannot be transferred again");
        }

        FileCopyUtils.copy(this.fileItem.getInputStream(), Files.newOutputStream(dest));
    }

    /**
     * Determine whether the multipart content is still available.
     * If a temporary file has been moved, the content is no longer available.
     */
    protected boolean isAvailable() {
        // If in memory, it's available.
        if (this.fileItem.isInMemory()) {
            return true;
        }
        // Check actual existence of temporary file.
        if (this.fileItem instanceof DiskFileItem) {
            return ((DiskFileItem) this.fileItem).getStoreLocation().exists();
        }
        // Check whether current file size is different than original one.
        return (this.fileItem.getSize() == this.size);
    }

    @Override
    public String toString() {
        return "MultipartFile[field=\"" + this.fileItem.getFieldName() + "\"" + (this.fileItem.getName() != null ? ", filename=" + this.fileItem.getName() : "")
                + (this.fileItem.getContentType() != null ? ", contentType=" + this.fileItem.getContentType() : "") + ", size=" + this.fileItem.getSize() + "]";
    }
}
