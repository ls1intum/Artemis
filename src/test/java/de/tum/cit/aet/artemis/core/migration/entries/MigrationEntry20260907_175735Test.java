package de.tum.cit.aet.artemis.core.migration.entries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.core.config.migration.MigrationIncompleteException;
import de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20260907_175735;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.lecture.api.LectureAttachmentApi;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO;

/**
 * The entry that moves the files the lecture attachment migration left behind, and above all the fact that running it twice is harmless.
 * <p>
 * There is no row to update, so a run that stops halfway leaves no inconsistency and every decision is made again from the filesystem on the next start. That is the property
 * worth pinning: an attachment whose file has already been moved must be left alone, a source that is not there must count as done, a source that could not be copied must not
 * be deleted, and one attachment that fails must not take the rest of the run down with it.
 * <p>
 * Real files are written under the configured upload root with random ids, rather than the upload root being redirected, because that root is static and the suite runs in
 * parallel.
 */
class MigrationEntry20260907_175735Test {

    private static final String CONTENT_IN_LECTURE_DIRECTORY = "the file the changelog left behind";

    @BeforeAll
    static void setUploadPath() {
        FilePathConverter.setFileUploadPath(readFileUploadPathFromConfig());
    }

    @SuppressWarnings("unchecked")
    private static Path readFileUploadPathFromConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = MigrationEntry20260907_175735Test.class.getClassLoader().getResourceAsStream("config/application-artemis.yml")) {
            Map<String, Object> config = yaml.load(inputStream);
            Map<String, Object> artemis = (Map<String, Object>) config.get("artemis");
            return Path.of((String) artemis.get("file-upload-path"));
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to read file-upload-path from the test configuration", exception);
        }
    }

    private final List<AttachmentFileLocationDTO> attachments = new ArrayList<>();

    private final List<Path> created = new ArrayList<>();

    private MigrationEntry20260907_175735 entry;

    @BeforeEach
    void setup() {
        LectureAttachmentApi api = mock(LectureAttachmentApi.class);
        // Keyset paging, so the second call for the same set comes back empty and the loop in the entry terminates.
        when(api.findAttachmentFileLocationsAfter(anyLong(), anyInt()))
                .thenAnswer(invocation -> attachments.stream().filter(attachment -> attachment.attachmentId() > invocation.<Long>getArgument(0)).toList());
        entry = new MigrationEntry20260907_175735(Optional.of(api));
    }

    @AfterEach
    void cleanUp() throws IOException {
        for (Path path : created.reversed()) {
            // A directory this test created may be shared with another test running in parallel, so only an empty one is removed.
            if (Files.isDirectory(path)) {
                try (Stream<Path> entries = Files.list(path)) {
                    if (entries.findAny().isPresent()) {
                        continue;
                    }
                }
            }
            Files.deleteIfExists(path);
        }
    }

    @Test
    void shouldMoveTheFileIntoTheUnitDirectoryAndDoNothingOnASecondRun() throws IOException {
        Attachment attachment = register();
        write(attachment.inLectureDirectory(), CONTENT_IN_LECTURE_DIRECTORY);

        entry.execute();

        assertThat(attachment.inUnitDirectory()).hasContent(CONTENT_IN_LECTURE_DIRECTORY);
        assertThat(attachment.inLectureDirectory()).doesNotExist();

        entry.execute();

        assertThat(attachment.inUnitDirectory()).hasContent(CONTENT_IN_LECTURE_DIRECTORY);
    }

    /**
     * The state a run that was interrupted after the move leaves for the attachments it had already done, and the state the overwhelming majority of attachments are in from the
     * start: nothing lies in a lecture directory, so there is nothing to do.
     */
    @Test
    void shouldTreatAMissingSourceAsAlreadyDone() throws IOException {
        Attachment attachment = register();
        write(attachment.inUnitDirectory(), "uploaded into the unit");

        entry.execute();

        assertThat(attachment.inUnitDirectory()).hasContent("uploaded into the unit");
    }

    /**
     * When both directories hold a file the unit is the one that is served, so the entry has nothing to move. The file in the lecture directory is left where it is rather than
     * deleted, because the entry did not copy it and cannot know that it is the same file.
     */
    @Test
    void shouldNotDeleteASourceItDidNotCopy() throws IOException {
        Attachment attachment = register();
        write(attachment.inUnitDirectory(), "the file that is served");
        write(attachment.inLectureDirectory(), CONTENT_IN_LECTURE_DIRECTORY);

        entry.execute();

        assertThat(attachment.inUnitDirectory()).hasContent("the file that is served");
        assertThat(attachment.inLectureDirectory()).hasContent(CONTENT_IN_LECTURE_DIRECTORY);
    }

    /**
     * One attachment that cannot be moved does not stop the ones after it, and is reported at the end so that the entry is not recorded as done. Here the unit directory cannot
     * be created because a regular file already occupies its path, which is the closest a test can get to the kind of filesystem failure this has to survive.
     * <p>
     * Both halves matter. Stopping at the first failure would leave every following attachment unmoved for a reason that has nothing to do with it, and returning normally would
     * have {@code MigrationService} write the changelog row, after which the file that stayed behind would never be looked at again.
     */
    @Test
    void shouldKeepGoingAfterOneAttachmentFailsAndReportTheRunAsIncomplete() throws IOException {
        Attachment failing = register();
        write(failing.inLectureDirectory(), CONTENT_IN_LECTURE_DIRECTORY);
        write(failing.inUnitDirectory().getParent(), "not a directory");

        Attachment following = register();
        write(following.inLectureDirectory(), CONTENT_IN_LECTURE_DIRECTORY);

        assertThatExceptionOfType(MigrationIncompleteException.class).isThrownBy(() -> entry.execute()).withMessageContaining("1 attachment file(s) could not be moved");

        assertThat(failing.inLectureDirectory()).hasContent(CONTENT_IN_LECTURE_DIRECTORY);
        assertThat(following.inUnitDirectory()).hasContent(CONTENT_IN_LECTURE_DIRECTORY);
        assertThat(following.inLectureDirectory()).doesNotExist();
    }

    /**
     * The retry the report above buys. The attachment that failed is attempted again on the next run, and once the obstacle is gone the file moves and the run reports success,
     * which is what finally lets the changelog row be written.
     */
    @Test
    void shouldMoveTheFileOnTheNextRunOnceTheObstacleIsGone() throws IOException {
        Attachment failing = register();
        write(failing.inLectureDirectory(), CONTENT_IN_LECTURE_DIRECTORY);
        Path obstacle = failing.inUnitDirectory().getParent();
        write(obstacle, "not a directory");

        assertThatExceptionOfType(MigrationIncompleteException.class).isThrownBy(() -> entry.execute());

        Files.delete(obstacle);

        assertThatCode(() -> entry.execute()).doesNotThrowAnyException();
        created.add(failing.inUnitDirectory());
        assertThat(failing.inUnitDirectory()).hasContent(CONTENT_IN_LECTURE_DIRECTORY);
        assertThat(failing.inLectureDirectory()).doesNotExist();
    }

    /**
     * Registers one attachment with the mocked repository, with ids and a filename no other test in the suite uses.
     *
     * @return the two candidate locations of its file
     */
    private Attachment register() {
        long attachmentVideoUnitId = ThreadLocalRandom.current().nextLong(800_000_000L, 899_999_999L);
        long lectureId = ThreadLocalRandom.current().nextLong(700_000_000L, 799_999_999L);
        String filename = "migration_" + UUID.randomUUID() + ".pdf";
        attachments.add(new AttachmentFileLocationDTO(attachments.size() + 1L, attachmentVideoUnitId, lectureId, filename));
        return new Attachment(new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnitId, filename).path(),
                new FileSystemLocation.LectureAttachment(lectureId, filename).path());
    }

    private void write(Path path, String content) throws IOException {
        created.add(path.getParent());
        FileUtils.writeStringToFile(path.toFile(), content, StandardCharsets.UTF_8);
        created.add(path);
    }

    private record Attachment(Path inUnitDirectory, Path inLectureDirectory) {
    }
}
