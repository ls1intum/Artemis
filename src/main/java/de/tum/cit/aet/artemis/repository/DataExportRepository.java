package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.DataExport;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for a data export entity.
 */
@Profile(PROFILE_CORE)
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
     * Find all data exports that need to be deleted. This includes all data exports that have a creation date older than 7 days
     *
     * @param thresholdDate the date to filter data exports, typically 7 days before today.
     * @return a set of data exports that need to be deleted
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.creationFinishedDate IS NOT NULL
                AND dataExport.creationFinishedDate < :thresholdDate
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

    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.dataExportState = 2
            """)
    Set<DataExport> findAllSuccessfullyCreatedDataExports();
}
