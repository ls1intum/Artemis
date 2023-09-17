package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for a data export entity.
 */
@Repository
public interface DataExportRepository extends JpaRepository<DataExport, Long> {

    /**
     * Find a data export by its ID and throw an {@link EntityNotFoundException} if it could not be found.
     *
     * @param dataExportId the id of the data export to find
     * @return the data export for the given id
     * @throws EntityNotFoundException if the data export could not be found
     */
    default DataExport findByIdElseThrow(long dataExportId) {
        return findById(dataExportId).orElseThrow(() -> {
            throw new EntityNotFoundException("Could not find data export with id: " + dataExportId);
        });
    }

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
            WHERE dataExport.dataExportState = 0 OR dataExport.dataExportState = 1
            """)
    Set<DataExport> findAllToBeCreated();

    /**
     * Find all data exports that need to be deleted. This includes all data exports that have a creation date older than 7 days
     *
     * @return a set of data exports that need to be deleted
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.creationFinishedDate is NOT NULL
                  AND dataExport.creationFinishedDate < :#{T(java.time.ZonedDateTime).now().minusDays(7)}
            """)
    Set<DataExport> findAllToBeDeleted();

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
    List<DataExport> findAllDataExportsByUserIdOrderByRequestDateDesc(long userId);

    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.dataExportState = 2
            """)
    Set<DataExport> findAllSuccessfullyCreatedDataExports();
}
