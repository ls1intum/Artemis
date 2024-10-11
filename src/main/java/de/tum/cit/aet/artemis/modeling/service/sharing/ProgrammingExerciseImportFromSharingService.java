package de.tum.in.www1.artemis.service.sharing;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.sharing.SharingMultipartZipFile;
import de.tum.in.www1.artemis.domain.sharing.SharingSetupInfo;
import de.tum.in.www1.artemis.exception.SharingException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportFromFileService;

@Service
@Profile("sharing")
public class ProgrammingExerciseImportFromSharingService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportFromSharingService.class);

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
     * Imports a programming exercise from the Sharing platform.
     * It reuses the implementation of ProgrammingExerciseImportFromFileService for importing the exercise from a Zip file.
     *
     * @param sharingSetupInfo Containing sharing and exercise data needed for the import
     */
    public ProgrammingExercise importProgrammingExerciseFromSharing(SharingSetupInfo sharingSetupInfo) throws SharingException, IOException, GitAPIException, URISyntaxException {
        SharingMultipartZipFile zipFile = exerciseSharingService.getCachedBasketItem(sharingSetupInfo.getSharingInfo());
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (sharingSetupInfo.getExercise().getCourseViaExerciseGroupOrCourseMember() == null) {
            sharingSetupInfo.getExercise().setCourse(sharingSetupInfo.getCourse());
        }
        return this.programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(sharingSetupInfo.getExercise(), zipFile, sharingSetupInfo.getCourse(), user, true);
    }
}
