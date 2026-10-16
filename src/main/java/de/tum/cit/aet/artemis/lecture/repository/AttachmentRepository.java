package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentStudentVersionStateDTO;

/**
 * Spring Data repository for the Attachment entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface AttachmentRepository extends ArtemisJpaRepository<Attachment, Long> {

    /**
     * Finds every attachment of the given lecture, reached through the attachment video unit that owns it.
     * <p>
     * An attachment names no lecture of its own any more, so the lecture is the one of its unit. This backs the route that serves a file under the lecture attachment path,
     * which exists because {@code 20260905235721_changelog.xml} turned the attachments that hung off a lecture into attachment video units without moving their files and
     * because markdown written years ago links to them there. That route used to see only the attachments whose file was still in the lecture directory; it now sees every
     * attachment of the lecture, and {@code Attachment.fileLocation} finds each one's file wherever it lies. Serving one whose file has since moved into its unit's directory
     * under the lecture path is the point rather than a side effect: it is what keeps an old link working after the file has been moved. Nothing here reads the shape of the
     * stored value.
     * <p>
     * The lecture and its course are fetched because the caller resolves the course from them for its authorization check.
     *
     * @param lectureId the lecture to look up
     * @return the attachments of the units of that lecture
     */
    @Query("""
            SELECT attachment
            FROM Attachment attachment
                JOIN FETCH attachment.attachmentVideoUnit unit
                JOIN FETCH unit.lecture lecture
                JOIN FETCH lecture.course
            WHERE lecture.id = :lectureId
            """)
    List<Attachment> findAllInLecture(@Param("lectureId") Long lectureId);

    /**
     * Projects what is needed to locate the file of every attachment with an id above the given one whose link names a file this application stores, ordered by id.
     * <p>
     * This backs {@code MigrationEntry20260907_175735}, which has to look at every attachment in the installation. It reads the column rather than {@code Attachment#getLink},
     * which builds a URL, and it loads no entity: the four values are all the two candidate locations of an attachment file need. Keyset paging on the primary key is used
     * because the entry runs over the whole table and moves files while it does, so a page must not shift under it.
     * <p>
     * The three exclusions are {@code FileSystemLocation#refersToStoredFile} written as SQL, and they are what keeps the entry from treating something that is not a stored file
     * as one. An attachment may point at a document hosted elsewhere, and the last segment of {@code https://example.org/lecture-notes.pdf} is a filename in shape only: taking
     * it as one would have the entry look for {@code notes.pdf} in the lecture directory, and if some other attachment of that lecture genuinely has a file of that name, move
     * that file into the wrong unit and strand the attachment it belongs to. The blank check is there for the same reason, so that a row holding an empty string does not
     * resolve to the directory itself.
     * <p>
     * The scheme exclusion is a plain colon test rather than the RFC 3986 scheme pattern the Java side matches, because a regex is spelled differently on PostgreSQL and MySQL
     * while {@code LIKE} is not. The two agree on every value this column can hold: a stored filename cannot contain a colon, since {@code FileUtil#checkAndSanitizeFilename}
     * allows only letters, digits, {@code _}, {@code .} and {@code -}. Any colon therefore means the value is not a filename, which is the question being asked. Matching only
     * {@code ://} would have let {@code file:/lecture/notes.pdf} through, because a URI authority is optional.
     *
     * @param minimumAttachmentId only attachments with a larger id are returned
     * @param pageable            how many to return
     * @return the file locations of that page of attachments, ordered by attachment id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO(attachment.id, unit.id, lecture.id, attachment.link)
            FROM Attachment attachment
                JOIN attachment.attachmentVideoUnit unit
                JOIN unit.lecture lecture
            WHERE attachment.id > :minimumAttachmentId
                AND attachment.link IS NOT NULL
                AND TRIM(attachment.link) <> ''
                AND attachment.link NOT LIKE '/%'
                AND attachment.link NOT LIKE '%:%'
            ORDER BY attachment.id
            """)
    List<AttachmentFileLocationDTO> findAttachmentFileLocationsAfter(@Param("minimumAttachmentId") long minimumAttachmentId, Pageable pageable);

    /**
     * Stores the filename of a newly written student version, but only while the attachment still has the file the student version belongs to. The file is identified by its
     * version and hash: two saves that start from the same version store different files under the same version number. Only this column is written, so a concurrent change
     * to other attachment fields is not overwritten.
     *
     * @param attachmentId           the id of the attachment
     * @param studentVersionFilename the stored filename of the new student version
     * @param expectedVersion        the version of the file the student version was made for
     * @param expectedSha256Hash     the hash of that file
     * @return 1 when stored, 0 when another file has replaced that one in the meantime
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Attachment a
            SET a.studentVersion = :studentVersionFilename
            WHERE a.id = :attachmentId
                AND (a.version = :expectedVersion OR (:expectedVersion IS NULL AND a.version IS NULL))
                AND (a.sha256Hash = :expectedSha256Hash OR (:expectedSha256Hash IS NULL AND a.sha256Hash IS NULL))
            """)
    int updateStudentVersionIfFileUnchanged(@Param("attachmentId") long attachmentId, @Param("studentVersionFilename") String studentVersionFilename,
            @Param("expectedVersion") Integer expectedVersion, @Param("expectedSha256Hash") String expectedSha256Hash);

    /**
     * Reads the stored student version reference and the file version of an attachment.
     *
     * @param attachmentId the id of the attachment
     * @return the state, or empty if the attachment does not exist
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.AttachmentStudentVersionStateDTO(a.studentVersion, a.version, a.sha256Hash)
            FROM Attachment a
            WHERE a.id = :attachmentId
            """)
    Optional<AttachmentStudentVersionStateDTO> findStudentVersionStateById(@Param("attachmentId") long attachmentId);

    /**
     * Stores a regenerated student version, but only while everything it was generated from is unchanged: the attachment still has the same file version and student
     * version reference, and the current slide deck hides exactly the given slides. The check and the write are one statement, so a regeneration that ran on an older state
     * does not replace a newer student version or show a slide hidden in the meantime; the caller discards its file and starts over instead.
     *
     * @param attachmentId             the id of the attachment
     * @param attachmentVideoUnitId    the id of its attachment video unit
     * @param newStudentVersion        the stored filename of the regenerated student version
     * @param expectedVersion          the file version the student version was generated from
     * @param expectedSha256Hash       the hash of that file
     * @param expectedStudentVersion   the stored student version value the regeneration started from, or null if there was none
     * @param expectedHiddenSlideIds   the ids of the hidden slides that were left out; never empty
     * @param expectedHiddenSlideCount the number of those slides
     * @return 1 when stored, 0 when something changed
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Attachment a
            SET a.studentVersion = :newStudentVersion
            WHERE a.id = :attachmentId
                AND (a.version = :expectedVersion OR (:expectedVersion IS NULL AND a.version IS NULL))
                AND (a.sha256Hash = :expectedSha256Hash OR (:expectedSha256Hash IS NULL AND a.sha256Hash IS NULL))
                AND (a.studentVersion = :expectedStudentVersion OR (:expectedStudentVersion IS NULL AND a.studentVersion IS NULL))
                AND (
                    SELECT COUNT(s.id)
                    FROM Slide s
                    WHERE s.attachmentVideoUnit.id = :attachmentVideoUnitId
                        AND s.hidden IS NOT NULL
                        AND s.superseded = FALSE
                ) = :expectedHiddenSlideCount
                AND NOT EXISTS (
                    SELECT s.id
                    FROM Slide s
                    WHERE s.attachmentVideoUnit.id = :attachmentVideoUnitId
                        AND s.hidden IS NOT NULL
                        AND s.superseded = FALSE
                        AND s.id NOT IN :expectedHiddenSlideIds
                )
            """)
    int publishStudentVersionIfUnchanged(@Param("attachmentId") long attachmentId, @Param("attachmentVideoUnitId") long attachmentVideoUnitId,
            @Param("newStudentVersion") String newStudentVersion, @Param("expectedVersion") Integer expectedVersion, @Param("expectedSha256Hash") String expectedSha256Hash,
            @Param("expectedStudentVersion") String expectedStudentVersion, @Param("expectedHiddenSlideIds") Collection<Long> expectedHiddenSlideIds,
            @Param("expectedHiddenSlideCount") long expectedHiddenSlideCount);

    /**
     * Removes the student version reference, but only if the attachment still points at the given file.
     * <p>
     * Student version filenames are unique per write, so a concurrent writer that has published a newer student version changes the value and this statement matches nothing.
     * The caller must delete the old file only when this returns 1.
     *
     * @param attachmentId                   the id of the attachment
     * @param expectedStudentVersionFilename the stored filename the caller wants to remove
     * @return the number of updated attachments
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Attachment a
            SET a.studentVersion = NULL
            WHERE a.id = :attachmentId
                AND a.studentVersion = :expectedStudentVersionFilename
            """)
    int clearStudentVersionIfUnchanged(@Param("attachmentId") long attachmentId, @Param("expectedStudentVersionFilename") String expectedStudentVersionFilename);

    /**
     * Update an attachment's display page numbers unconditionally: for a calling run whose processing
     * state never recorded an attachment version (no PDF was ever detected for it), so there is nothing
     * to compare against. {@link #updateDisplayPageNumbersIfVersionMatches} is the guarded counterpart,
     * for a run that does have a version to protect against a concurrent content change.
     *
     * @param attachmentId       the attachment to update
     * @param displayPageNumbers the new mapping
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE Attachment a
            SET a.displayPageNumbers = :displayPageNumbers
            WHERE a.id = :attachmentId
            """)
    void updateDisplayPageNumbers(@Param("attachmentId") Long attachmentId, @Param("displayPageNumbers") List<Integer> displayPageNumbers);

    /**
     * Update an attachment's display page numbers, atomically: only while it still carries the
     * expected version.
     * <p>
     * A content-triggered requeue clears this mapping and bumps the version when new content
     * replaces this attachment's PDF (see {@code LectureContentProcessingService#cleanupForReprocessing}).
     * Matching on the version here is what stops an ingestion callback whose run predates that
     * requeue from restoring stale page numbers over content that has already changed, without
     * needing a transaction spanning the processing-state write and this one.
     * <p>
     * This used to accept a nullable {@code expectedVersion} with a {@code (:expectedVersion IS NULL OR
     * a.version = :expectedVersion)} guard, imposing no constraint when null. That shape is not portable:
     * PostgreSQL's extended query protocol determines a prepared statement's parameter types from the
     * static SQL text alone, before any value is ever bound, and a bare {@code ? IS NULL} cannot be typed
     * from syntax — so the query fails with {@code 42P18 could not determine data type of parameter} on
     * every call, independent of whether the actual bound value is null. {@link #updateDisplayPageNumbers}
     * is the unguarded counterpart for a caller with nothing to pin, so this method's parameter is never
     * null and needs no such guard.
     *
     * @param attachmentId       the attachment to update
     * @param displayPageNumbers the new mapping
     * @param expectedVersion    the attachment version the calling run's processing state recorded
     * @return 1 when applied, 0 when the attachment's version has since changed
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE Attachment a
            SET a.displayPageNumbers = :displayPageNumbers
            WHERE a.id = :attachmentId
            AND a.version = :expectedVersion
            """)
    int updateDisplayPageNumbersIfVersionMatches(@Param("attachmentId") Long attachmentId, @Param("displayPageNumbers") List<Integer> displayPageNumbers,
            @Param("expectedVersion") Integer expectedVersion);

    /**
     * Clear an attachment's display page numbers while the recovery that claimed its unit's interrupted content change still
     * holds that claim (see {@code LectureUnitProcessingStateRecoveryRepository#claimStrandedRun}). Once an edit has taken the
     * unit over, which clears the claim, the mapping may be a newer run's and is left alone.
     *
     * @param attachmentId  the attachment to clear
     * @param lectureUnitId the unit the attachment belongs to
     * @param claimToken    the recovery's claim
     * @return 1 when cleared, 0 when the claim no longer holds
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE Attachment a
            SET a.displayPageNumbers = NULL
            WHERE a.id = :attachmentId
            AND EXISTS (
                SELECT ps.id FROM LectureUnitProcessingState ps
                WHERE ps.lectureUnit.id = :lectureUnitId AND ps.claimToken = :claimToken AND ps.ingestionJobToken IS NULL
            )
            """)
    int clearDisplayPageNumbersIfRecoveryClaimHolds(@Param("attachmentId") Long attachmentId, @Param("lectureUnitId") Long lectureUnitId, @Param("claimToken") String claimToken);

}
