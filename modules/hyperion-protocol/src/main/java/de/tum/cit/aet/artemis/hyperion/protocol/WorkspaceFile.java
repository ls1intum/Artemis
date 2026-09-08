package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A regular file in a bounded snapshot. Binary wrappers and executable bits are preserved without archive extraction. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkspaceFile(String path, @JsonInclude byte[] content, boolean executable) {

    public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    public WorkspaceFile {
        if (path == null || path.isBlank() || path.length() > 512 || path.startsWith("/") || path.contains("\\") || path.contains(":")
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Snapshot paths must be bounded relative paths");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.equalsIgnoreCase(".git")) {
                throw new IllegalArgumentException("Snapshot path contains a forbidden segment");
            }
        }
        Objects.requireNonNull(content);
        if (content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Snapshot file exceeds the byte limit");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    /** The uncompressed file size, without allocating a defensive content copy. */
    public int size() {
        return content.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkspaceFile file && path.equals(file.path) && executable == file.executable && Arrays.equals(content, file.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, executable, Arrays.hashCode(content));
    }

    @Override
    public String toString() {
        return "WorkspaceFile[path=" + path + ", bytes=" + content.length + ", executable=" + executable + "]";
    }
}
