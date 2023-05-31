package de.tum.in.www1.artemis.repository;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Repository
public interface AttachmentUnitRepository extends JpaRepository<AttachmentUnit, Long> {

    @Query("""
            SELECT attachmentUnit
            FROM AttachmentUnit attachmentUnit
                LEFT JOIN FETCH attachmentUnit.slides slides
            WHERE attachmentUnit.id = :attachmentUnitId
            """)
    AttachmentUnit findOneWithSlides(@Param("attachmentUnitId") long attachmentUnitId);

    @NotNull
    default AttachmentUnit findByIdElseThrow(Long attachmentUnitId) throws EntityNotFoundException {
        return findById(attachmentUnitId).orElseThrow(() -> new EntityNotFoundException("AttachmentUnit", attachmentUnitId));
    }

}
