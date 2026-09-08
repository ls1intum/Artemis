package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

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
        TarArchiveInputStream tar = tar(entry -> {
            entry.setName(PREFIX + "/../0001__junit.xml");
            return entry;
        }, "<testsuite/>".getBytes(StandardCharsets.UTF_8));
        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX));
    }

    @Test
    void rejectsAnOversizedEntry() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long oversize = CollectedReports.MAX_FILE_BYTES + 1;
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry(PREFIX + "/0001__junit.xml");
            entry.setSize(oversize);
            tarOut.putArchiveEntry(entry);
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

    @Test
    void rejectsAnArchiveWithMoreEntriesThanTheEntryCap() {
        // Both byte caps accumulate content bytes only, so an archive of zero-byte entries grows the returned map unbounded unless the entry cap stops it.
        TarArchiveInputStream tar = zeroByteEntryTar(CollectedReports.MAX_ARCHIVE_ENTRIES + 1);

        assertThatExceptionOfType(CollectedReports.RejectedReportException.class).isThrownBy(() -> CollectedReports.read(tar, PREFIX))
                .withMessageContaining("more than " + CollectedReports.MAX_ARCHIVE_ENTRIES + " entries");
    }

    @Test
    void acceptsAnArchiveAtExactlyTheEntryCap() throws Exception {
        TarArchiveInputStream tar = zeroByteEntryTar(CollectedReports.MAX_ARCHIVE_ENTRIES);

        assertThat(CollectedReports.read(tar, PREFIX)).hasSize(CollectedReports.MAX_ARCHIVE_ENTRIES);
    }

    /** Builds a tar of {@code entryCount} zero-byte report entries under the expected prefix: the cheapest archive that passes every byte cap. */
    private static TarArchiveInputStream zeroByteEntryTar(int entryCount) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (int i = 0; i < entryCount; i++) {
                TarArchiveEntry entry = new TarArchiveEntry(PREFIX + "/" + i + "__junit.xml");
                entry.setSize(0);
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()));
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
