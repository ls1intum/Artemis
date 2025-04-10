package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.FileUpload;
import de.tum.cit.aet.artemis.core.domain.FileUploadEntityType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface FileUploadRepository extends ArtemisJpaRepository<FileUpload, Long> {

    /***
     * Get a single file upload entity by its path
     *
     * @param path path of the file upload
     *
     * @return The file upload if exists, null otherwise.
     */
    FileUpload findFileUploadByPath(String path);

    /***
     * The value "f.entityType = 0" represents a conversation, given by the enum {{@link FileUploadEntityType}}
     *
     * @return List of file uploads that do not have a conversation entity connected to them
     */
    @Query("""
            SELECT f
            FROM FileUpload f
                LEFT JOIN Conversation c ON f.entityId = c.id
            WHERE f.entityType = 0
                AND c.id IS NULL
            """)
    List<FileUpload> findOrphanedConversationReferences();

    /***
     * Finds all file uploads that are not connected to an entity and were created before a given date
     *
     * @param cutoffDate is the date that defines from when the file uploads should be returned. E.g. to give some time before deleting.
     *
     * @return List of file uploads that do not have an entity and were created before a given date
     */
    @Query("""
            SELECT f
            FROM FileUpload f
            WHERE (f.entityType IS NULL OR f.entityId IS NULL)
                AND f.creationDate < :cutoffDate
            """)
    List<FileUpload> findNullEntityReferences(@Param("cutoffDate") ZonedDateTime cutoffDate);
}
