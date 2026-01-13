package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for a data export entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface DataExportRepository extends ArtemisJpaRepository<DataExport, Long> {

    /**
     * Find all data exports that need to be created. This includes all data exports that are currently in the state IN_CREATION (the export was not completed then) or
     * REQUESTED.
     * 0 = REQUESTED, 1 = IN_CREATION
     *
     * @return a set of data exports that need to be created
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.dataExportState = 0
                OR dataExport.dataExportState = 1
            """)
    Set<DataExport> findAllToBeCreated();

    /**
     * Find all data exports that need to be deleted. This includes all data exports that have a creation date older than 7 days and that have not been deleted or failed before
     *
     * @param thresholdDate the date to filter data exports, typically 7 days before today.
     * @return a set of data exports that need to be deleted
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.creationFinishedDate IS NOT NULL
                AND dataExport.creationFinishedDate < :thresholdDate
                AND dataExport.dataExportState < 4
            """)
    Set<DataExport> findAllToBeDeleted(@Param("thresholdDate") ZonedDateTime thresholdDate);

    /**
     * Find all data exports for the given user ordered by their request date descending.
     * We use this sorting because this allows us to always get the latest data export without a doing any other calculations.
     * <p>
     * This is relevant if more than one data export exists that can be downloaded.
     * This can happen if the user had requested a data export that was created and the admin requested another data export for the same user that has been created.
     *
     * @param userId the id of the user to find the data exports for
     * @return a list of data exports for the given user ordered by their request date descending
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.user.id = :userId
            ORDER BY dataExport.createdDate DESC
            """)
    List<DataExport> findAllDataExportsByUserIdOrderByRequestDateDesc(@Param("userId") long userId);

    /**
     * Find all data exports with their associated users, ordered by creation date descending.
     * This is used for the admin overview of all data exports.
     *
     * @return a list of all data exports with user information
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            LEFT JOIN FETCH dataExport.user
            ORDER BY dataExport.createdDate DESC
            """)
    List<DataExport> findAllWithUserOrderByCreatedDateDesc();

    /**
     * Find all data exports with pagination support, ordered by creation date descending.
     * This is used for the admin overview of all data exports with pagination.
     *
     * @param pageable the pagination information
     * @return a page of data exports with user information
     */
    @Query(value = """
            SELECT dataExport
            FROM DataExport dataExport
            LEFT JOIN FETCH dataExport.user
            ORDER BY dataExport.createdDate DESC
            """, countQuery = """
            SELECT COUNT(dataExport)
            FROM DataExport dataExport
            """)
    Page<DataExport> findAllWithUserOrderByCreatedDateDesc(Pageable pageable);

    /**
     * Find a data export by id with user information.
     *
     * @param dataExportId the id of the data export
     * @return the data export with user information if found
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            LEFT JOIN FETCH dataExport.user
            WHERE dataExport.id = :dataExportId
            """)
    Optional<DataExport> findByIdWithUser(@Param("dataExportId") long dataExportId);
}
