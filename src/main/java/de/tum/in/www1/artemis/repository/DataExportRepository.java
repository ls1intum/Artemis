package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface DataExportRepository extends JpaRepository<DataExport, Long> {

    default DataExport findByIdElseThrow(Long dataExportId) {
        return findById(dataExportId).orElseThrow(() -> {
            throw new EntityNotFoundException("Could not find data export with id: " + dataExportId);
        });
    }
}
