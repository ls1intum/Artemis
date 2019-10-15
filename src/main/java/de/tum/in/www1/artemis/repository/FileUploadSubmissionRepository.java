package de.tum.in.www1.artemis.repository;

import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.FileUploadSubmission;

/**
 * Spring Data JPA repository for the FileUploadSubmission entity.
 */
@Repository
public interface FileUploadSubmissionRepository extends GenericSubmissionRepository<FileUploadSubmission> {
}
