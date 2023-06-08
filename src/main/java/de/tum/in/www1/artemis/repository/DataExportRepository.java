package de.tum.in.www1.artemis.repository;

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
     * Find all data exports that need to be created needs. This includes all data exports that are currently in the state IN_CREATION (the export was not completed then) or
     * requested.
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
     *
     * @return a set of data exports that need to be deleted
     */
    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.creationDate is NOT NULL
            AND dataExport.creationDate < :#{T(java.time.ZonedDateTime).now().minusDays(7)}
            """)
    Set<DataExport> findAllToBeDeleted();

}
