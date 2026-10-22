package de.tum.cit.aet.artemis.atlas.science;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportRequestDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceCourseConsentRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEnabledCourseRepository;
import de.tum.cit.aet.artemis.atlas.repository.ScienceResearchExportAuditRepository;
import de.tum.cit.aet.artemis.atlas.service.ScienceCourseService;
import de.tum.cit.aet.artemis.atlas.test_repository.ScienceEventTestRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * A failed research export must not leave its partial CSV behind, since the file holds real interaction data. The
 * failures are injected through mocks, which the shared integration context cannot do without affecting other tests.
 */
class ScienceResearchExportCleanupTest {

    private static final ScienceResearchExportRequestDTO REQUEST = new ScienceResearchExportRequestDTO(Set.of(1L), null, null, null, "Failing study");

    @TempDir
    private Path tempDir;

    private final ScienceEventTestRepository scienceEventRepository = mock(ScienceEventTestRepository.class);

    private final ScienceResearchExportAuditRepository auditRepository = mock(ScienceResearchExportAuditRepository.class);

    private ScienceCourseService scienceCourseService;

    @BeforeEach
    void setUp() {
        scienceCourseService = serviceWritingTo(tempDir);
    }

    private ScienceCourseService serviceWritingTo(Path exportDirectory) {
        return new ScienceCourseService(mock(CourseRepository.class), mock(UserRepository.class), mock(ScienceEnabledCourseRepository.class),
                mock(ScienceCourseConsentRepository.class), scienceEventRepository, auditRepository, mock(AuthorizationCheckService.class),
                new TempFileUtilService(exportDirectory));
    }

    @Test
    void deletesTheFileWhenTheAuditCannotBeStored() {
        when(auditRepository.save(any())).thenThrow(new IllegalStateException("audit failed"));

        assertThatThrownBy(() -> scienceCourseService.createResearchExport(REQUEST)).hasMessage("audit failed");

        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void deletesTheFileWhenReadingTheEventsFails() {
        when(scienceEventRepository.findMaxIdForResearchExport(any(), any(), any(), any())).thenThrow(new IllegalStateException("read failed"));

        assertThatThrownBy(() -> scienceCourseService.createResearchExport(REQUEST)).hasMessage("read failed");

        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void reportsAFileThatCannotBeCreatedAsABadRequest() throws Exception {
        // A regular file where the temporary directory should be makes creating the export file fail.
        Path notADirectory = Files.createFile(tempDir.resolve("not-a-directory"));

        assertThatThrownBy(() -> serviceWritingTo(notADirectory).createResearchExport(REQUEST)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void anExportThatCannotBeOpenedIsReported() {
        Path missingExport = tempDir.resolve("science-research-export-missing.csv");

        assertThatThrownBy(() -> new ScienceCourseService.ScienceResearchExport(missingExport, "checksum", 0).open()).isInstanceOf(NoSuchFileException.class);
    }
}
