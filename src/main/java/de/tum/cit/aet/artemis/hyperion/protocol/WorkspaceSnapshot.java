package de.tum.cit.aet.artemis.hyperion.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable regular-file snapshot with a canonical content identity independent of transport ordering. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkspaceSnapshot(@JsonInclude List<WorkspaceFile> files) {

    public static final int MAX_FILES = 10_000;

    public static final long MAX_BYTES = 32L * 1024 * 1024;

    public WorkspaceSnapshot {
        if (files == null || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Snapshot exceeds the file count limit");
        }
        files = files.stream().sorted(Comparator.comparing(WorkspaceFile::path)).toList();
        HashSet<String> paths = new HashSet<>();
        long bytes = 0;
        for (WorkspaceFile file : files) {
            if (!paths.add(file.path())) {
                throw new IllegalArgumentException("Snapshot contains duplicate paths");
            }
            for (int slash = file.path().indexOf('/'); slash >= 0; slash = file.path().indexOf('/', slash + 1)) {
                if (paths.contains(file.path().substring(0, slash))) {
                    throw new IllegalArgumentException("Snapshot file is also a directory");
                }
            }
            bytes += file.size();
            if (bytes > MAX_BYTES) {
                throw new IllegalArgumentException("Snapshot exceeds the total byte limit");
            }
        }
    }

    /** SHA-256 over length-prefixed UTF-8 paths, executable flags and file bytes in sorted path order. */
    public String sha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (WorkspaceFile file : files) {
                byte[] path = file.path().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(path.length).array());
                digest.update(path);
                digest.update((byte) (file.executable() ? 1 : 0));
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(file.size()).array());
                digest.update(file.content());
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM does not support SHA-256", e);
        }
    }
}
