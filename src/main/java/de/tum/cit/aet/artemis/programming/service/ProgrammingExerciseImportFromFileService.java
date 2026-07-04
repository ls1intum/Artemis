package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService.BUILD_PLAN_FILE_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.service.LegacyBuildPlanConverterService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseImportFromFileService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportFromFileService.class);

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ZipFileService zipFileService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ProgrammingExerciseImportRepositoryService programmingExerciseImportRepositoryService;

    private final FileService fileService;

    private final ProfileService profileService;

    private final BuildPlanRepository buildPlanRepository;

    private final TempFileUtilService tempFileUtilService;

    private final Optional<LegacyBuildPlanConverterService> legacyBuildPlanConverterService;

    public ProgrammingExerciseImportFromFileService(ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ZipFileService zipFileService, StaticCodeAnalysisService staticCodeAnalysisService,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, ProgrammingExerciseImportRepositoryService programmingExerciseImportRepositoryService,
            FileService fileService, ProfileService profileService, BuildPlanRepository buildPlanRepository, TempFileUtilService tempFileUtilService,
            Optional<LegacyBuildPlanConverterService> legacyBuildPlanConverterService) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.zipFileService = zipFileService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.programmingExerciseImportRepositoryService = programmingExerciseImportRepositoryService;
        this.fileService = fileService;
        this.profileService = profileService;
        this.buildPlanRepository = buildPlanRepository;
        this.tempFileUtilService = tempFileUtilService;
        this.legacyBuildPlanConverterService = legacyBuildPlanConverterService;
    }

    /**
     * Imports a programming exercise from an uploaded zip file that has previously been downloaded from an Artemis instance.
     * It first extracts the contents of the zip file, then creates a programming exercise (same process as creating a new one),
     * then deletes the template content initially pushed to the repositories and copies over the extracted content
     *
     * @param originalProgrammingExercise the programming exercise that should be imported
     * @param zipFile                     the zip file that contains the exercise
     * @param course                      the course to which the exercise should be added
     * @param user                        the user initiating the import
     * @param isImportFromSharing         flag whether file import (false) of sharing import
     * @return the imported programming exercise
     **/
    public ProgrammingExercise importProgrammingExerciseFromFile(ProgrammingExercise originalProgrammingExercise, MultipartFile zipFile, Course course, User user,
            boolean isImportFromSharing) throws IOException, GitAPIException {
        if (!"zip".equals(FilenameUtils.getExtension(zipFile.getOriginalFilename()))) {
            throw new BadRequestAlertException("The file is not a zip file", "programmingExercise", "fileNotZip");
        }
        Path importExerciseDir = null;
        ProgrammingExercise newProgrammingExercise;
        try {
            importExerciseDir = tempFileUtilService.createTempDirectory("imported-exercise-dir");
            Path exerciseFilePath = tempFileUtilService.createTempFile(importExerciseDir, "exercise-for-import", ".zip");

            if (isImportFromSharing) {
                // Exercises from Sharing are currently exported in a slightly different zip structure containing an additional root dir
                try (Stream<Path> walk = Files.walk(importExerciseDir)) {
                    List<Path> directories = walk.filter(Files::isDirectory).toList();
                    if (directories.isEmpty()) {
                        throw new BadRequestAlertException("No directories found for Sharing import", "programmingExercise", "noSharingDirFound");
                    }
                    importExerciseDir = directories.getFirst();
                }
            }

            zipFile.transferTo(exerciseFilePath);
            zipFileService.extractZipFileRecursively(exerciseFilePath);
            checkDetailsJsonExists(importExerciseDir);
            checkRepositoriesExist(importExerciseDir);

            handleLegacyLocalCIProgrammingExercise(originalProgrammingExercise);

            originalProgrammingExercise.setCourse(course);
            originalProgrammingExercise.setTestCasesChanged(false);

            programmingExerciseValidationService.validateNewProgrammingExerciseSettings(originalProgrammingExercise, course);
            newProgrammingExercise = programmingExerciseCreationUpdateService.createProgrammingExercise(originalProgrammingExercise, false, true);

            if (Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled())) {
                staticCodeAnalysisService.createDefaultCategories(newProgrammingExercise);
            }
            Path pathToDirectoryWithImportedContent = exerciseFilePath.toAbsolutePath().getParent().resolve(FilenameUtils.getBaseName(exerciseFilePath.toString()));
            copyEmbeddedFiles(pathToDirectoryWithImportedContent);
            programmingExerciseImportRepositoryService.importRepositoriesFromFile(newProgrammingExercise, importExerciseDir, user);

            Optional<String> importedJenkinsBuildPlan = Optional.empty();
            if (profileService.isJenkinsActive()) {
                importedJenkinsBuildPlan = readBuildPlanIfExisting(pathToDirectoryWithImportedContent);
            }

            try {
                programmingExerciseRepositoryService.adjustProjectNames(getProgrammingExerciseFromDetailsFile(importExerciseDir).getTitle(), newProgrammingExercise);
            }
            catch (GitAPIException | IOException e) {
                log.error("Error during adjustment of placeholders of ProgrammingExercise {}", newProgrammingExercise.getTitle(), e);
            }

            if (importedJenkinsBuildPlan.isPresent()) {
                buildPlanRepository.setBuildPlanForExercise(importedJenkinsBuildPlan.orElseThrow(), newProgrammingExercise);
            }
            newProgrammingExercise = programmingExerciseCreationUpdateService.setupBuildPlansAndTriggerInitialBuilds(newProgrammingExercise);
        }
        finally {
            // want to make sure the directories are deleted, even if an exception is thrown
            fileService.scheduleDirectoryPathForRecursiveDeletion(importExerciseDir, 5);
        }
        return newProgrammingExercise;
    }

    /**
     * Reads a build plan if it exists in the extracted zip file.
     * If the file cannot be read, the build plan is skipped.
     *
     * @param importExerciseDir the directory where the extracted zip file is located
     * @return the build plan content, if present and readable
     */
    private Optional<String> readBuildPlanIfExisting(Path importExerciseDir) throws IOException {
        Path buildPlanPath = importExerciseDir.resolve(BUILD_PLAN_FILE_NAME);
        if (Files.exists(buildPlanPath)) {
            return Optional.of(Files.readString(buildPlanPath, StandardCharsets.UTF_8));
        }
        return Optional.empty();
    }

    /**
     * Overloaded method setting the isImportFromSharing flag to false as default
     *
     * @param programmingExerciseForImport the programming exercise that should be imported
     * @param zipFile                      the zip file that contains the exercise
     * @param course                       the course to which the exercise should be added
     * @param user                         the user initiating the import
     * @return the imported programming exercise
     * @throws IOException if there is an error reading the file
     */
    public ProgrammingExercise importProgrammingExerciseFromFile(ProgrammingExercise programmingExerciseForImport, MultipartFile zipFile, Course course, User user)
            throws IOException, GitAPIException, URISyntaxException {
        return this.importProgrammingExerciseFromFile(programmingExerciseForImport, zipFile, course, user, false);
    }

    /**
     * Copy embedded files from the extracted zip file to the markdown folder, so they can be used in the problem statement
     *
     * @param importExerciseDir the directory where the extracted zip file is located
     **/
    private void copyEmbeddedFiles(Path importExerciseDir) throws IOException {
        Path embeddedFilesDir = importExerciseDir.resolve("files");

        if (!Files.exists(embeddedFilesDir)) {
            return;
        }
        try (var embeddedFiles = Files.list(embeddedFilesDir)) {
            for (Path file : embeddedFiles.toList()) {
                Path targetPath = FilePathConverter.getMarkdownFilePath().resolve(file.getFileName());
                if (!Files.exists(targetPath)) {
                    FileUtils.copyFile(file.toFile(), targetPath.toFile());
                }
            }
        }
    }

    /**
     * Reads the programming exercise details from the JSON file in the extracted zip path.
     *
     * @param extractedZipPath the path to the extracted zip file containing the exercise details
     * @return the programming exercise object deserialized from the JSON file
     * @throws IOException if there is an error reading the file
     */
    private ProgrammingExercise getProgrammingExerciseFromDetailsFile(Path extractedZipPath) throws IOException {
        var exerciseJsonPath = retrieveExerciseJsonPath(extractedZipPath);
        ObjectMapper objectMapper = JsonObjectMapper.get();

        try {
            return objectMapper.readValue(exerciseJsonPath.toFile(), ProgrammingExercise.class);
        }
        catch (IOException e) {
            throw new BadRequestAlertException("The JSON file for the programming exercise is not valid or was not found.", "programmingExercise", "exerciseJsonNotValidOrFound");
        }
    }

    private void checkRepositoriesExist(Path path) throws IOException {
        checkRepositoryForTypeExists(path, RepositoryType.TEMPLATE);
        checkRepositoryForTypeExists(path, RepositoryType.SOLUTION);
        checkRepositoryForTypeExists(path, RepositoryType.TESTS);
    }

    private void checkRepositoryForTypeExists(Path path, RepositoryType repoType) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            if (stream.filter(Files::isDirectory).map(f -> f.getFileName().toString()).filter(name -> name.endsWith("-" + repoType.getName())).count() != 1) {
                throw new BadRequestAlertException("The zip file doesn't contain the " + repoType.getName() + " repository or it does not follow the naming scheme.",
                        "programmingExercise", "repositoriesInZipNotValid");
            }
        }
    }

    /**
     * Checks if the Exercise-Details.json file exists in the extracted zip directory.
     *
     * @param path the path to the extracted zip directory
     * @throws IOException              if there is an error reading the directory
     * @throws BadRequestAlertException if the Exercise-Details.json file is not found or not unique
     */
    private void checkDetailsJsonExists(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            long count = stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith(ProgrammingExerciseExportService.EXPORTED_EXERCISE_DETAILS_FILE_PREFIX))
                    .filter(file -> file.toString().endsWith(".json")).count();

            if (count == 0) {
                throw new BadRequestAlertException("The Exercise-Details.json file is missing in the uploaded zip file.", "programmingExercise", "exerciseJsonNotFound");
            }

            if (count > 1) {
                throw new BadRequestAlertException("Multiple Exercise-Details.json files found in the uploaded zip file.", "programmingExercise", "multipleExerciseJsonFiles");
            }
        }
    }

    /**
     * Retrieves the path to the Exercise-Details.json file in the extracted zip path.
     *
     * @param dirPath the path to the extracted zip file containing the exercise details
     * @return the path to the Exercise-Details.json file
     * @throws IOException if there is an error reading the file
     */
    private Path retrieveExerciseJsonPath(Path dirPath) throws IOException {
        List<Path> result;
        try (Stream<Path> stream = Files.walk(dirPath)) {
            // if we do not convert the file name to a string, the second filter always returns false
            // for the third filter, we need to convert it to a string as well as a path doesn't contain a file extension

            result = stream.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().startsWith("Exercise-Details"))
                    .filter(file -> file.toString().endsWith(".json")).toList();
        }

        if (result.size() != 1) {
            throw new BadRequestAlertException("There are either no JSON files or more than one JSON file in the directory!", "programmingExercise", "exerciseJsonNotValidOrFound");
        }
        return result.getFirst();
    }

    /**
     * This handles the build config where the buildPlanConfiguration is still in the old format.
     *
     * @param programmingExercise the exercise to handle
     * @throws JsonProcessingException when serialization failed
     */
    private void handleLegacyLocalCIProgrammingExercise(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        if (!profileService.isLocalCIActive() || programmingExercise.getBuildConfig() == null) {
            return;
        }

        final Optional<BuildPlanPhasesDTO> buildPlanPhasesDTO = legacyBuildPlanConverterService.orElseThrow().convertLegacyBuildPlanConfiguration(programmingExercise);
        programmingExercise.getBuildConfig().setBuildScript(null);

        if (buildPlanPhasesDTO.isEmpty()) {
            try {
                // check that it is in the valid format
                BuildPlanPhasesDTO.fromBuildPlanConfiguration(programmingExercise.getBuildConfig().getBuildPlanConfiguration());
            }
            catch (JsonProcessingException e) {
                // if not reset it
                programmingExercise.getBuildConfig().setBuildPlanConfiguration(null);
            }
            return;
        }

        programmingExercise.getBuildConfig().setBuildPlanConfiguration(buildPlanPhasesDTO.orElseThrow().toBuildPlanConfiguration());
    }
}
