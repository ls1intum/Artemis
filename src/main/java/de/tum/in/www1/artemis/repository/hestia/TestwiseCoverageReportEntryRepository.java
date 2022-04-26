package de.tum.in.www1.artemis.repository.hestia;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;

/**
 * Spring Data JPA repository for the TestwiseCoverageReportEntry entity.
 */
@Repository
public interface TestwiseCoverageReportEntryRepository extends JpaRepository<TestwiseCoverageReportEntry, Long> {

    @Query("""
            SELECT e FROM TestwiseCoverageReportEntry e
            LEFT JOIN FETCH ProgrammingExerciseTestCase t
            WHERE t.id = :#{#testCaseId}
            """)
    Set<TestwiseCoverageReportEntry> findByTestCaseId(@Value("testCaseId") Long testCaseId);
}
