package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;

/**
 * Spring Data repository for the Attachment entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("""
            SELECT a
            FROM Attachment a
            WHERE a.lecture.id = :lectureId
            """)
    List<Attachment> findAllByLectureId(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT a
            FROM Attachment a
            WHERE a.attachmentUnit.lecture.id = :lectureId
            """)
    List<Attachment> findAllByAttachmentUnit_LectureId(@Param("lectureId") Long lectureId);

    @NotNull
    default Attachment findByIdElseThrow(Long attachmentId) throws EntityNotFoundException {
        return findById(attachmentId).orElseThrow(() -> new EntityNotFoundException("Attachment", attachmentId));
    }
}
