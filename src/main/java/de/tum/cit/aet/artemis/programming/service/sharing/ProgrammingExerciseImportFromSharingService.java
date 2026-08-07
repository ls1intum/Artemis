package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
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

    private final CourseRepository courseRepository;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public ProgrammingExerciseImportFromSharingService(ProgrammingExerciseImportFromFileService programmingExerciseImportFromFileService,
            ExerciseSharingService exerciseSharingService, UserRepository userRepository, CourseRepository courseRepository,
            CompetencyExerciseLinkService competencyExerciseLinkService) {
        this.programmingExerciseImportFromFileService = programmingExerciseImportFromFileService;
        this.exerciseSharingService = exerciseSharingService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * Imports a programming exercise referenced by the Sharing Platform.
     * <p>
     * Steps:
     * <ol>
     * <li>Fetch the exported exercise ZIP via {@link ExerciseSharingService getCachedBasketItem(SharingSetupInfo.SharingInfoDTO)}</li>
     * <li>Resolve the target course from {@code sharingSetupInfo.courseId()} and attach it to a course exercise</li>
     * <li>Resolve the competency links the author picked in the create form, which are competencies of the target
     * course: the client drops the shared exercise's own links before it shows the form</li>
     * <li>Run the standard ZIP import using {@link ProgrammingExerciseImportFromFileService}</li>
     * </ol>
     * The import runs as the current user returned by {@link UserRepository#getUserWithAuthorities()}.
     * </p>
     *
     * @param sharingSetupInfo container with the basket reference, the exercise model to import, and (optionally) the target course
     * @return the persisted {@link ProgrammingExercise}
     *
     * @throws SharingException   if the setup info is inconsistent (e.g., exercise null or missing target course), or if the basket ZIP cannot be obtained
     * @throws IOException        if reading the ZIP or related I/O fails
     * @throws GitAPIException    if VCS operations during import fail
     * @throws URISyntaxException if an internal URI cannot be constructed
     */
    public ProgrammingExercise importProgrammingExerciseFromSharing(SharingSetupInfoDTO sharingSetupInfo)
            throws SharingException, IOException, GitAPIException, URISyntaxException {
        if (sharingSetupInfo.exercise() == null) {
            throw new SharingException("Exercise should not be null for import");
        }
        if (sharingSetupInfo.courseId() == 0) {
            throw new SharingException("Target course is missing for import");
        }
        // Map the request body to the transient exercise the import pipeline works on. Null collections become empty
        // ones, exactly as the previous entity binding produced them.
        ProgrammingExercise exercise = sharingSetupInfo.exercise().toEntity();
        try (SharingMultipartZipFile zip = exerciseSharingService.getCachedBasketItem(sharingSetupInfo.sharingInfo())) {

            User user = userRepository.getUserWithAuthorities();
            Course course = courseRepository.findByIdElseThrow(sharingSetupInfo.courseId());

            // An exam exercise reaches its course over the exercise group, which the request only references by id. A
            // course exercise gets the target course, overwriting the source course the exported details carry: the
            // import writes the same course a line further down anyway, and the competency links below need it.
            if (!exercise.isExamExercise()) {
                exercise.setCourse(course);
            }

            // The request record does not bind the competency links itself: they need managed competencies, which only
            // this service resolves. The import runs through the creation pipeline, which reads the links off the
            // exercise, exactly as the entity request body used to leave them there.
            competencyExerciseLinkService.updateCompetencyLinks(sharingSetupInfo.exercise(), exercise);

            return this.programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(exercise, zip, course, user, true);
        }
    }
}
