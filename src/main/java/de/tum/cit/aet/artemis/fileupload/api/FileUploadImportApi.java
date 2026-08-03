package de.tum.cit.aet.artemis.fileupload.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadExerciseImportService;

/**
 * API for functionality regarding the import of file upload exercises (but not for general upload functionality).
 */
@Conditional(FileUploadEnabled.class)
@Controller
@Lazy
public class FileUploadImportApi extends AbstractFileModuleApi {

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final FileUploadExerciseImportService fileUploadExerciseImportService;

    public FileUploadImportApi(FileUploadExerciseRepository fileUploadExerciseRepository, FileUploadExerciseImportService fileUploadExerciseImportService) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.fileUploadExerciseImportService = fileUploadExerciseImportService;
    }

    public Optional<FileUploadExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        return fileUploadExerciseRepository.findUniqueWithCompetenciesByTitleAndCourseId(title, courseId);
    }

    public FileUploadExercise findWithGradingCriteriaByIdElseThrow(Long exerciseId) {
        return fileUploadExerciseRepository.findWithGradingCriteriaByIdElseThrow(exerciseId);
    }

    public FileUploadExercise importFileUploadExercise(final FileUploadExercise newExercise, FileUploadExercise sourceExercise) {
        return fileUploadExerciseImportService.importFileUploadExercise(newExercise, sourceExercise);
    }

    public Optional<FileUploadExercise> importFileUploadExercise(final long sourceExerciseId, final FileUploadExercise newExercise) {
        final Optional<FileUploadExercise> optionalSourceExercise = fileUploadExerciseRepository
                .findByIdWithExampleSubmissionsAndResultsAndCompetenciesAndGradingCriteria(sourceExerciseId);
        return optionalSourceExercise.map(sourceExercise -> fileUploadExerciseImportService.importFileUploadExercise(newExercise, sourceExercise));
    }
}
