package de.tum.cit.aet.artemis.fileupload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;

/**
 * Tests how the export names the file of a file upload submission.
 */
class FileUploadSubmissionExportServiceTest {

    private final FileUploadSubmissionExportService service = new FileUploadSubmissionExportService(mock(ExerciseRepository.class), mock(ZipFileService.class),
            mock(FileService.class));

    private static FileUploadSubmission submissionWithFilePath(String filePath) {
        var submission = new FileUploadSubmission();
        submission.setFilePath(filePath);
        return submission;
    }

    @Test
    void fileEndingIsTheEndOfTheFileNameFromTheFirstDot() {
        assertThat(service.getFileEndingForSubmission(submissionWithFilePath("/api/fileupload/files/1/2/solution.tar.gz"))).isEqualTo(".tar.gz");
    }

    @Test
    void fileNameWithoutADotHasNoEnding() {
        assertThat(service.getFileEndingForSubmission(submissionWithFilePath("/api/fileupload/files/1/2/solution"))).isEmpty();
    }

    @Test
    void submissionWithoutFileHasNoEnding() {
        assertThat(service.getFileEndingForSubmission(submissionWithFilePath(null))).isEmpty();
    }
}
