package de.tum.cit.aet.artemis.sharing.service;

import java.io.IOException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.api.ProgrammingExerciseArchiveApi;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.sharing.config.SharingEnabled;
import de.tum.cit.aet.artemis.sharing.dto.SharingSetupInfoDTO;

/** Imports the selected basket archive into the course authorized by the programming module. */
@Service
@Conditional(SharingEnabled.class)
@Lazy
public class ProgrammingExerciseImportFromSharingService {

    private final ProgrammingExerciseArchiveApi archiveApi;

    private final ExerciseSharingService exerciseSharingService;

    public ProgrammingExerciseImportFromSharingService(ProgrammingExerciseArchiveApi archiveApi, ExerciseSharingService exerciseSharingService) {
        this.archiveApi = archiveApi;
        this.exerciseSharingService = exerciseSharingService;
    }

    /**
     * Checks the target before contacting the platform and closes the archive after import.
     *
     * @param sharingSetupInfo the exercise, target course and basket item to import
     * @return the newly persisted exercise, including its build configuration
     * @throws SharingException if the target or exercise is missing or the basket cannot be fetched
     * @throws IOException      if the archive or its repositories cannot be imported
     */
    public ProgrammingExerciseResponseDTO importProgrammingExerciseFromSharing(SharingSetupInfoDTO sharingSetupInfo) throws SharingException, IOException {
        if (sharingSetupInfo.exercise() == null) {
            throw new SharingException("Exercise should not be null for import");
        }
        if (sharingSetupInfo.courseId() == 0) {
            throw new SharingException("Target course is missing for import");
        }
        var importArchive = archiveApi.prepareImport(sharingSetupInfo.exercise(), sharingSetupInfo.courseId());
        try (SharingMultipartZipFile zip = exerciseSharingService.getCachedBasketItem(sharingSetupInfo.sharingInfo())) {
            return importArchive.apply(zip);
        }
    }
}
