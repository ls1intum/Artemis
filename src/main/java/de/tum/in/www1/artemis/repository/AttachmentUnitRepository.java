package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Repository
public interface AttachmentUnitRepository extends JpaRepository<AttachmentUnit, Long> {

    @Query("""
            SELECT attachmentUnit
            FROM AttachmentUnit attachmentUnit
            WHERE attachmentUnit.lecture.id = :#{#lectureId}
            AND attachmentUnit.attachment.attachmentType = :#{#attachmentType}
            """)
    Set<AttachmentUnit> findByLectureIdAndAttachmentType(@Param("lectureId") Long lectureId, @Param("attachmentType") AttachmentType attachmentType);

}
