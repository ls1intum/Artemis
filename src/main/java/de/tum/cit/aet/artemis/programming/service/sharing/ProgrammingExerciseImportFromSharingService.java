package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportFromFileService;

@Service
@Profile(Constants.PROFILE_SHARING)
@Lazy
public class ProgrammingExerciseImportFromSharingService {

    private final ProgrammingExerciseImportFromFileService programmingExerciseImportFromFileService;

    private final ExerciseSharingService exerciseSharingService;

    private final UserRepository userRepository;

    public ProgrammingExerciseImportFromSharingService(ProgrammingExerciseImportFromFileService programmingExerciseImportFromFileService,
            ExerciseSharingService exerciseSharingService, UserRepository userRepository) {
        this.programmingExerciseImportFromFileService = programmingExerciseImportFromFileService;
        this.exerciseSharingService = exerciseSharingService;
        this.userRepository = userRepository;
    }

    /**
     * Imports a programming exercise from the Sharing Platform.
     * This method reuses the implementation of ProgrammingExerciseImportFromFileService for importing the exercise from a Zip file.
     *
     * @param sharingSetupInfo Containing sharing and exercise data needed for the import
     * @return The imported ProgrammingExercise
     * @throws SharingException if the sharing setup info is invalid or the import fails
     */
    public ProgrammingExercise importProgrammingExerciseFromSharing(SharingSetupInfo sharingSetupInfo) throws SharingException, IOException, GitAPIException, URISyntaxException {
        if (sharingSetupInfo.exercise() == null) {
            throw new SharingException("Exercise is null?");
        }
        Optional<SharingMultipartZipFile> zipFileO = exerciseSharingService.getCachedBasketItem(sharingSetupInfo.sharingInfo());
        if (zipFileO.isEmpty()) {
            throw new SharingException("Failed to retrieve exercise zip file from sharing platform");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (sharingSetupInfo.exercise().getCourseViaExerciseGroupOrCourseMember() == null) {
            sharingSetupInfo.exercise().setCourse(sharingSetupInfo.course());
        }
        return this.programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(sharingSetupInfo.exercise(), zipFileO.get(), sharingSetupInfo.course(), user, true);
    }
}
