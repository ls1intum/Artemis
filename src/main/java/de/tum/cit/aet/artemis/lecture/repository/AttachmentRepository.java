package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO;

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
                AND attachment.link NOT LIKE '%://%'
            ORDER BY attachment.id
            """)
    List<AttachmentFileLocationDTO> findAttachmentFileLocationsAfter(@Param("minimumAttachmentId") long minimumAttachmentId, Pageable pageable);

}
