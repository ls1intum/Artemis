package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportFromFileService;

/**
 * Orchestrates importing programming exercises from the Sharing Platform into Artemis.
 * <p>
 * Delegates the actual ZIP-based import to {@link ProgrammingExerciseImportFromFileService}.
 * Active only when {@link SharingEnabled} evaluates to true.
 * </p>
 */
@Service
@Conditional(SharingEnabled.class)
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
     * Imports a programming exercise referenced by the Sharing Platform.
     * <p>
     * Steps:
     * <ol>
     * <li>Fetch the exported exercise ZIP via {@link ExerciseSharingService getCachedBasketItem(SharingSetupInfo.SharingInfoDTO)}</li>
     * <li>Resolve the target course: if the exercise has no course/exercise group set, use
     * {@code sharingSetupInfo.course()}, otherwise fail with {@link SharingException}</li>
     * <li>Run the standard ZIP import using {@link ProgrammingExerciseImportFromFileService}</li>
     * </ol>
     * The import runs as the current user returned by {@link UserRepository#getUserWithGroupsAndAuthorities()}.
     * </p>
     *
     * @param sharingSetupInfo container with the basket reference, the exercise model to import, and (optionally) the target course
     * @return the persisted {@link ProgrammingExercise}
     *
     * @throws SharingException   if the setup info is inconsistent (e.g., exercise null or missing target course),
     *                                or if the basket ZIP cannot be obtained
     * @throws IOException        if reading the ZIP or related I/O fails
     * @throws GitAPIException    if VCS operations during import fail
     * @throws URISyntaxException if an internal URI cannot be constructed
     */
    public ProgrammingExercise importProgrammingExerciseFromSharing(SharingSetupInfo sharingSetupInfo) throws SharingException, IOException, GitAPIException, URISyntaxException {
        if (sharingSetupInfo.exercise() == null) {
            throw new SharingException("Exercise should not be null?");
        }
        try (SharingMultipartZipFile zip = exerciseSharingService.getCachedBasketItem(sharingSetupInfo.sharingInfo())) {

            User user = userRepository.getUserWithGroupsAndAuthorities();

            if (sharingSetupInfo.exercise().getCourseViaExerciseGroupOrCourseMember() == null) {
                if (sharingSetupInfo.course() == null) {
                    throw new SharingException("Target course is missing for import");
                }
                sharingSetupInfo.exercise().setCourse(sharingSetupInfo.course());
            }
            return this.programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(sharingSetupInfo.exercise(), zip, sharingSetupInfo.course(), user, true);
        }
    }
}
