package de.tum.cit.aet.artemis.core.config.migration.entries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.config.migration.MigrationEntry;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.lecture.api.LectureAttachmentApi;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO;

/**
 * Moves the file of every attachment that still lies in a lecture directory into the directory of the attachment video unit that owns it.
 * <p>
 * {@code 20260905235721_changelog.xml} turned every attachment that hung off a lecture directly into an attachment video unit of that lecture, but left its file where it was,
 * under {@code uploads/attachments/lecture/{lectureId}}, because a changelog cannot move a file. Everything else about such an attachment now says the unit: its served URL, the
 * directory a replacement file is written into, the directory its slides and its student version go in. This entry is what finally makes its file agree, and it is the only
 * thing that can, because moving a file is not something SQL can express.
 * <p>
 * <b>What this retires.</b> {@link FileSystemLocation#ofAttachment} asks the filesystem which of the two directories holds a file, which is the one place in that type that
 * does not answer from metadata alone. Once this entry has run on every installation, that fallback can be deleted and an attachment file is once again located by computation.
 * It cannot be deleted in the same release: Liquibase runs before the application context is up and this entry runs on {@code ApplicationReadyEvent} after it, so during the
 * first start of this release requests are already being served while the files are still where the changelog left them.
 * <p>
 * <b>Resumable rather than transactional.</b> There is no row to update, because a column holds nothing but a filename and the directory is derived, so a half-finished run
 * leaves no inconsistency behind: an attachment whose file has been moved resolves to the unit directory, one whose file has not resolves to the lecture directory, and both are
 * served correctly. That is what lets every decision here be made per attachment and be safe to make again:
 * <ul>
 * <li>a file already in the unit directory is left alone, so a second run moves nothing;</li>
 * <li>a source that is not there is treated as done, which is also the answer for the attachment that never had a file in a lecture directory, and that is the overwhelming
 * majority;</li>
 * <li>{@link Files#move} without {@code REPLACE_EXISTING} renames within the upload directory where it can and otherwise copies and then deletes, so the source is never
 * removed unless the copy succeeded;</li>
 * <li>one attachment that fails is counted and logged and the run continues, because there is nothing the others gain from stopping.</li>
 * </ul>
 * No authorization stand-in is installed. {@code SecurityUtils.setAuthorizationObject()} is what a migration entry needs when a query it makes is gated on a principal or when a
 * write it makes is audited, and this entry neither writes a row nor makes a query that resolves an authentication; see the guideline in
 * {@code documentation/docs/developer/guidelines/database.mdx}.
 * <p>
 * The empty lecture directories are deliberately left behind. Removing a directory is not something this entry can do safely without knowing that nothing else ever wrote into
 * it, and an empty directory costs nothing.
 */
public class MigrationEntry20260907_175735 extends MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry20260907_175735.class);

    /**
     * How many attachment file locations are read per query. The projection is four values wide, so this is small; the point of paging at all is that the entry moves files
     * while it walks the table and must not have a page shift under it, which keyset paging on the primary key rules out.
     */
    private static final int PAGE_SIZE = 1000;

    private final Optional<LectureAttachmentApi> lectureAttachmentApi;

    public MigrationEntry20260907_175735(Optional<LectureAttachmentApi> lectureAttachmentApi) {
        this.lectureAttachmentApi = lectureAttachmentApi;
    }

    @Override
    public void execute() {
        if (lectureAttachmentApi.isEmpty()) {
            log.info("Lecture module is not enabled, so there are no attachment files to move");
            return;
        }
        LectureAttachmentApi api = lectureAttachmentApi.get();

        int moved = 0;
        int skipped = 0;
        int failed = 0;
        long lastSeenAttachmentId = 0;

        List<AttachmentFileLocationDTO> page = api.findAttachmentFileLocationsAfter(lastSeenAttachmentId, PAGE_SIZE);
        while (!page.isEmpty()) {
            for (AttachmentFileLocationDTO location : page) {
                lastSeenAttachmentId = location.attachmentId();
                switch (move(location)) {
                    case MOVED -> moved++;
                    case ALREADY_DONE -> skipped++;
                    case FAILED -> failed++;
                }
            }
            page = api.findAttachmentFileLocationsAfter(lastSeenAttachmentId, PAGE_SIZE);
        }

        log.info("Moved {} attachment files out of their lecture directory into the directory of their attachment video unit, {} needed no move, {} failed", moved, skipped,
                failed);
    }

    /**
     * Moves one attachment file, if there is one to move.
     *
     * @param location the ids and filename of the attachment
     * @return what happened, so that the caller can count it
     */
    private Outcome move(AttachmentFileLocationDTO location) {
        Path inUnitDirectory = new FileSystemLocation.AttachmentVideoUnitFile(location.attachmentVideoUnitId(), location.storedFilename()).path();
        Path inLectureDirectory = new FileSystemLocation.LectureAttachment(location.lectureId(), location.storedFilename()).path();

        try {
            if (Files.exists(inUnitDirectory)) {
                return Outcome.ALREADY_DONE;
            }
            if (!Files.exists(inLectureDirectory)) {
                return Outcome.ALREADY_DONE;
            }
            Files.createDirectories(inUnitDirectory.getParent());
            Files.move(inLectureDirectory, inUnitDirectory);
            log.debug("Moved the file of attachment {} from {} to {}", location.attachmentId(), inLectureDirectory, inUnitDirectory);
            return Outcome.MOVED;
        }
        catch (IOException | RuntimeException exception) {
            log.error("Could not move the file of attachment {} from {} to {}. It stays where it is and is still served from there.", location.attachmentId(), inLectureDirectory,
                    inUnitDirectory, exception);
            return Outcome.FAILED;
        }
    }

    /**
     * What happened to one attachment file.
     */
    private enum Outcome {
        /**
         * The file was in the lecture directory and is now in the unit directory.
         */
        MOVED,
        /**
         * There was nothing to move: the file is already in the unit directory, or there is no file in the lecture directory.
         */
        ALREADY_DONE,
        /**
         * The move was attempted and did not succeed. The file is still readable where it was.
         */
        FAILED
    }

    @Override
    public String author() {
        return "krusche";
    }

    @Override
    public String date() {
        return "20260907_175735";
    }
}
