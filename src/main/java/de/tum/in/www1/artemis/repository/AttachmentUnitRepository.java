package de.tum.in.www1.artemis.repository.lecture_unit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Repository
public interface AttachmentUnitRepository extends JpaRepository<AttachmentUnit, Long> {
}
