package de.tum.cit.aet.artemis.core.test_repository;

import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.repository.DataExportRepository;

@Repository
public interface DataExportTestRepository extends DataExportRepository {

    @Query("""
            SELECT dataExport
            FROM DataExport dataExport
            WHERE dataExport.dataExportState = 2
            """)
    Set<DataExport> findAllSuccessfullyCreatedDataExports();
}
