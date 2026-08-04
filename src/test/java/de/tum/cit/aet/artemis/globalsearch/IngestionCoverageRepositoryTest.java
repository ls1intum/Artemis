package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * Repository tests for the {@link IngestionCoverageEntry} projection. Sorting and filtering the matrix across all courses
 * must be a single-table indexed read with no join to the course table, so these assert that the JSON per-type counts
 * round-trip, that {@code ORDER BY worst_rank / release_date / last_ingested_at} and the status filter return the right
 * rows with pagination, and that the id-set-scoped read works, against a real database.
 */
class IngestionCoverageRepositoryTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private IngestionCoverageRepository ingestionCoverageRepository;

    @BeforeEach
    void setUp() {
        ingestionCoverageRepository.deleteAll();
    }

    @Test
    void savesAndFindsByCourseIdWithJsonCountsRoundTripped() {
        IngestionCoverageEntry entry = entry(1L, 3, IngestionCoverageStatus.INCOMPLETE, ZonedDateTime.now(), ZonedDateTime.now());
        entry.setTypeCounts(List.of(new IngestionTypeCountDTO("exercise", 5, 4, 1, 0), new IngestionTypeCountDTO("slides", 2, 2, 0, 0)));
        ingestionCoverageRepository.save(entry);

        IngestionCoverageEntry found = ingestionCoverageRepository.findByCourseId(1L).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(IngestionCoverageStatus.INCOMPLETE);
        assertThat(found.getWorstRank()).isEqualTo(3);
        assertThat(found.getTypeCounts()).containsExactly(new IngestionTypeCountDTO("exercise", 5, 4, 1, 0), new IngestionTypeCountDTO("slides", 2, 2, 0, 0));
    }

    @Test
    void findsRowsForACourseIdSet() {
        ingestionCoverageRepository.save(entry(1L, 0, IngestionCoverageStatus.COMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));
        ingestionCoverageRepository.save(entry(2L, 0, IngestionCoverageStatus.COMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));
        ingestionCoverageRepository.save(entry(3L, 0, IngestionCoverageStatus.COMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));

        List<IngestionCoverageEntry> found = ingestionCoverageRepository.findAllByCourseIdIn(List.of(1L, 3L));

        assertThat(found).extracting(IngestionCoverageEntry::getCourseId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void sortsByWorstRankDescending() {
        ingestionCoverageRepository.save(entry(1L, 1, IngestionCoverageStatus.INCOMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));
        ingestionCoverageRepository.save(entry(2L, 9, IngestionCoverageStatus.INCOMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));
        ingestionCoverageRepository.save(entry(3L, 5, IngestionCoverageStatus.INCOMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));

        List<IngestionCoverageEntry> page = ingestionCoverageRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "worstRank"))).getContent();

        // Worst first: 9, 5, 1 -> courses 2, 3, 1.
        assertThat(page).extracting(IngestionCoverageEntry::getCourseId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void sortsByReleaseDateDescending() {
        ZonedDateTime now = ZonedDateTime.now();
        ingestionCoverageRepository.save(entry(1L, 0, IngestionCoverageStatus.COMPLETE, now.minusDays(10), now));
        ingestionCoverageRepository.save(entry(2L, 0, IngestionCoverageStatus.COMPLETE, now.minusDays(1), now));
        ingestionCoverageRepository.save(entry(3L, 0, IngestionCoverageStatus.COMPLETE, now.minusDays(5), now));

        List<IngestionCoverageEntry> page = ingestionCoverageRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "releaseDate"))).getContent();

        // Most recently released first: -1d, -5d, -10d -> courses 2, 3, 1.
        assertThat(page).extracting(IngestionCoverageEntry::getCourseId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void sortsByLastIngestedAtDescending() {
        ZonedDateTime now = ZonedDateTime.now();
        ingestionCoverageRepository.save(entry(1L, 0, IngestionCoverageStatus.COMPLETE, now, now.minusHours(10)));
        ingestionCoverageRepository.save(entry(2L, 0, IngestionCoverageStatus.COMPLETE, now, now.minusHours(1)));
        ingestionCoverageRepository.save(entry(3L, 0, IngestionCoverageStatus.COMPLETE, now, now.minusHours(5)));

        List<IngestionCoverageEntry> page = ingestionCoverageRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "lastIngestedAt"))).getContent();

        // Most recently ingested first: -1h, -5h, -10h -> courses 2, 3, 1.
        assertThat(page).extracting(IngestionCoverageEntry::getCourseId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void filtersByStatusWithPagination() {
        ingestionCoverageRepository.save(entry(1L, 5, IngestionCoverageStatus.INCOMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));
        ingestionCoverageRepository.save(entry(2L, 0, IngestionCoverageStatus.COMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));
        ingestionCoverageRepository.save(entry(3L, 4, IngestionCoverageStatus.INCOMPLETE, ZonedDateTime.now(), ZonedDateTime.now()));

        var incompletePage = ingestionCoverageRepository.findAllByStatus(IngestionCoverageStatus.INCOMPLETE, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "worstRank")));

        assertThat(incompletePage.getTotalElements()).isEqualTo(2);
        assertThat(incompletePage.getContent()).extracting(IngestionCoverageEntry::getCourseId).containsExactly(1L);
    }

    private IngestionCoverageEntry entry(long courseId, int worstRank, IngestionCoverageStatus status, ZonedDateTime releaseDate, ZonedDateTime lastIngestedAt) {
        IngestionCoverageEntry entry = new IngestionCoverageEntry();
        entry.setCourseId(courseId);
        entry.setWorstRank(worstRank);
        entry.setStatus(status);
        entry.setCourseTitle("Course " + courseId);
        entry.setReleaseDate(releaseDate);
        entry.setActive(true);
        entry.setSemester("WS2026");
        entry.setComputedAt(ZonedDateTime.now());
        entry.setLastIngestedAt(lastIngestedAt);
        return entry;
    }
}
