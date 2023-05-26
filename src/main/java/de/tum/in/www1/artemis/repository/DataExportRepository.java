package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
