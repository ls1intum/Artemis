package de.tum.in.www1.artemis.repository.lecture_module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_module.AttachmentModule;

/**
 * Spring Data JPA repository for the Attachment Module entity.
 */
@Repository
public interface AttachmentModuleRepository extends JpaRepository<AttachmentModule, Long> {
}
