package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Adversarial unit test for the hardened {@link CollectedReports} reader: the {@code copyOut} tar of the verifier-owned reports directory is untrusted, so a
 * symlinked/hardlinked/non-regular/path-escaping/oversized entry must be REJECTED before any byte is parsed. A linked or escaping entry could redirect the verifier to read a file
 * outside the reports dir; an oversized entry could exhaust memory. Each shape is built by hand and fed through the reader; a regular-file archive must still pass (no
 * over-rejection).
 */
class CollectedReportsTest {

    private static final String PREFIX = "solution";

    @Test
    void readsRegularFilesAndStripsThePrefix() throws Exception {
        TarArchiveInputStream tar = tar(entry -> {
            entry.setName(PREFIX + "/0001__junit.xml");
            return entry;
        }, "<testsuite/>".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> read = CollectedReports.read(tar, PREFIX);
        assertThat(read).containsOnlyKeys("0001__junit.xml");
        assertThat(new String(read.get("0001__junit.xml"), StandardCharsets.UTF_8)).isEqualTo("<testsuite/>");
    }

    @Test
    void rejectsASymlinkedEntry() {
        TarArchiveInputStream tar = linkTar(TarArchiveEntry.LF_SYMLINK, "/etc/passwd");
        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX));
    }

    @Test
    void rejectsAHardlinkedEntry() {
        TarArchiveInputStream tar = linkTar(TarArchiveEntry.LF_LINK, "../../etc/hosts");
        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX));
    }

    @Test
    void rejectsANonRegularEntry() {
        TarArchiveInputStream tar = typeTar(TarArchiveEntry.LF_FIFO);
        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX));
    }

    @Test
    void rejectsAPathEscapingEntry() {
        // A regular file whose name climbs above the reports prefix must be refused.
        TarArchiveInputStream tar = tar(entry -> {
            entry.setName(PREFIX + "/../0001__junit.xml");
            return entry;
        }, "<testsuite/>".getBytes(StandardCharsets.UTF_8));
        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX));
    }

    @Test
    void rejectsAnOversizedEntry() {
        // An entry whose ACTUAL content exceeds the per-file cap is refused after reading (a defense against a tar whose declared size understates the body).
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long oversize = CollectedReports.MAX_FILE_BYTES + 1;
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry(PREFIX + "/0001__junit.xml");
            entry.setSize(oversize);
            tarOut.putArchiveEntry(entry);
            // Stream the oversized body in chunks so the test does not hold it all in memory.
            byte[] chunk = new byte[1024 * 1024];
            long written = 0;
            while (written < oversize) {
                int n = (int) Math.min(chunk.length, oversize - written);
                tarOut.write(chunk, 0, n);
                written += n;
            }
            tarOut.closeArchiveEntry();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX));
    }

    /** Builds a single-entry tar whose entry is mutated by {@code mutator} (a regular file by default) carrying {@code content}. */
    private static TarArchiveInputStream tar(java.util.function.UnaryOperator<TarArchiveEntry> mutator, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = mutator.apply(new TarArchiveEntry(PREFIX + "/placeholder"));
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    /** Builds a single-entry tar whose entry is a link (symlink/hardlink) with the given link target. */
    private static TarArchiveInputStream linkTar(byte linkFlag, String linkTarget) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry link = new TarArchiveEntry(PREFIX + "/0001__junit.xml", linkFlag);
            link.setLinkName(linkTarget);
            tar.putArchiveEntry(link);
            tar.closeArchiveEntry();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }

    /** Builds a single-entry tar whose entry has the given (non-regular) type flag and no content. */
    private static TarArchiveInputStream typeTar(byte typeFlag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry(PREFIX + "/0001__junit.xml", typeFlag);
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
    }
}
