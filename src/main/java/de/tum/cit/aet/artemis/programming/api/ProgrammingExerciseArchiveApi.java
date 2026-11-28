package de.tum.cit.aet.artemis.programming.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.function.IOFunction;
import org.apache.commons.io.function.IOSupplier;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportFromFileService;

/**
 * Access to programming exercise archives for other modules. The wire contract uses the same
 * {@link ImportProgrammingExerciseRequestDTO} and {@link ProgrammingExerciseResponseDTO} as the programming REST endpoints.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class ProgrammingExerciseArchiveApi implements AbstractApi {

    private final ProgrammingExerciseRepository exerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    private final ProgrammingExerciseExportService exportService;

    private final ProgrammingExerciseImportFromFileService importService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public ProgrammingExerciseArchiveApi(ProgrammingExerciseRepository exerciseRepository, ProgrammingExerciseBuildConfigRepository buildConfigRepository,
            ProgrammingExerciseExportService exportService, ProgrammingExerciseImportFromFileService importService, UserRepository userRepository,
            CourseRepository courseRepository, CourseService courseService, AuthorizationCheckService authorizationCheckService,
            CompetencyExerciseLinkService competencyExerciseLinkService) {
        this.exerciseRepository = exerciseRepository;
        this.buildConfigRepository = buildConfigRepository;
        this.exportService = exportService;
        this.importService = importService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.authorizationCheckService = authorizationCheckService;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
    }

    /**
     * Supplies the settings used to preview and configure an archive import.
     *
     * @param archive the exercise archive
     * @return the exercise settings without the source id
     * @throws IOException if the archive cannot be read or the entry is missing
     */
    public ImportProgrammingExerciseRequestDTO readExerciseDetails(MultipartFile archive) throws IOException {
        return importService.readExerciseDetails(archive);
    }

    /**
     * Checks editor access before the caller obtains the archive. For an exam exercise, the exercise group's course
     * determines access. For a course exercise, the supplied target course replaces the source course in the request.
     *
     * @param request  exercise settings and competency links for the new exercise
     * @param courseId target course id for a course exercise
     * @return the operation that imports an archive into the authorized target
     */
    public IOFunction<MultipartFile, ProgrammingExerciseResponseDTO> prepareImport(ImportProgrammingExerciseRequestDTO request, long courseId) {
        var exercise = request.toEntity();
        var buildConfig = ProgrammingExerciseRequestDTO.buildConfigOf(request);
        var user = userRepository.getUserWithAuthorities();
        var course = exercise.isExamExercise() ? courseService.retrieveCourseOverExerciseGroupOrCourseId(exercise) : courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        return archive -> {
            if (!exercise.isExamExercise()) {
                exercise.setCourse(course);
            }
            competencyExerciseLinkService.updateCompetencyLinks(request, exercise);
            try {
                var imported = importService.importProgrammingExerciseFromFile(exercise, buildConfig, archive, course, user);
                return ProgrammingExerciseResponseDTO.of(imported, buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(imported.getId()));
            }
            catch (GitAPIException e) {
                throw new IOException("Could not import the repositories of the programming exercise", e);
            }
        };
    }

    /**
     * Checks editor access before the caller starts the export.
     *
     * @param exerciseId id of the programming exercise to export
     * @return the operation that creates the archive and supplies its path
     */
    public IOSupplier<Path> prepareExport(long exerciseId) {
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exerciseRepository.findByIdElseThrow(exerciseId), null);
        return () -> {
            var exercise = exerciseRepository.findByIdWithGradingCriteriaElseThrow(exerciseId);
            List<String> errors = new ArrayList<>();
            Path archive = exportService.exportProgrammingExerciseForDownload(exercise, errors);
            if (!errors.isEmpty()) {
                throw new IOException(String.join(", ", errors));
            }
            return archive;
        };
    }
}
