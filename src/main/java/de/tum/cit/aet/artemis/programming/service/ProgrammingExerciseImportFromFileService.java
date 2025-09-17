package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService.BUILD_PLAN_FILE_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

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

    private final RepositoryService repositoryService;

    private final GitService gitService;

    private final FileService fileService;

    private final ProfileService profileService;

    private final BuildPlanRepository buildPlanRepository;

    @Value("${artemis.temp-path}")
    private Path tempPath;

    public ProgrammingExerciseImportFromFileService(ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ZipFileService zipFileService, StaticCodeAnalysisService staticCodeAnalysisService,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, RepositoryService repositoryService, GitService gitService, FileService fileService,
            ProfileService profileService, BuildPlanRepository buildPlanRepository) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.zipFileService = zipFileService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.repositoryService = repositoryService;
        this.gitService = gitService;
        this.fileService = fileService;
        this.profileService = profileService;
        this.buildPlanRepository = buildPlanRepository;
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
            boolean isImportFromSharing) throws IOException, GitAPIException, URISyntaxException {
        if (!"zip".equals(FilenameUtils.getExtension(zipFile.getOriginalFilename()))) {
            throw new BadRequestAlertException("The file is not a zip file", "programmingExercise", "fileNotZip");
        }
        Path importExerciseDir = null;
        ProgrammingExercise newProgrammingExercise;
        try {
            importExerciseDir = Files.createTempDirectory(tempPath, "imported-exercise-dir");
            Path exerciseFilePath = Files.createTempFile(importExerciseDir, "exercise-for-import", ".zip");

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

            programmingExerciseValidationService.validateNewProgrammingExerciseSettings(originalProgrammingExercise, course);
            // TODO: creating the whole exercise (from template) is a bad solution in this case, we do not want the template content, instead we want the file content of the zip
            newProgrammingExercise = programmingExerciseCreationUpdateService.createProgrammingExercise(originalProgrammingExercise);
            if (Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled())) {
                staticCodeAnalysisService.createDefaultCategories(newProgrammingExercise);
            }
            Path pathToDirectoryWithImportedContent = exerciseFilePath.toAbsolutePath().getParent().resolve(FilenameUtils.getBaseName(exerciseFilePath.toString()));
            copyEmbeddedFiles(pathToDirectoryWithImportedContent);
            importRepositoriesFromFile(newProgrammingExercise, importExerciseDir, user);

            try {
                programmingExerciseRepositoryService.adjustProjectNames(getProgrammingExerciseFromDetailsFile(importExerciseDir).getTitle(), newProgrammingExercise);
            }
            catch (GitAPIException | IOException e) {
                log.error("Error during adjustment of placeholders of ProgrammingExercise {}", newProgrammingExercise.getTitle(), e);
            }

            newProgrammingExercise.setCourse(course);
            // It doesn't make sense to import a build plan on a local CI setup.
            if (profileService.isJenkinsActive()) {
                importBuildPlanIfExisting(newProgrammingExercise, pathToDirectoryWithImportedContent);
            }
            // TODO: we need to create the build configuration
        }
        finally {
            // want to make sure the directories are deleted, even if an exception is thrown
            fileService.scheduleDirectoryPathForRecursiveDeletion(importExerciseDir, 5);
        }
        return newProgrammingExercise;
    }

    /**
     * Imports a build plan if it exists in the extracted zip file
     * If the file cannot be read, the build plan is skipped
     *
     * @param programmingExercise the programming exercise for which the build plan should be imported
     * @param importExerciseDir   the directory where the extracted zip file is located
     */
    private void importBuildPlanIfExisting(ProgrammingExercise programmingExercise, Path importExerciseDir) {
        Path buildPlanPath = importExerciseDir.resolve(BUILD_PLAN_FILE_NAME);
        if (Files.exists(buildPlanPath)) {
            try {
                buildPlanRepository.setBuildPlanForExercise(FileUtils.readFileToString(buildPlanPath.toFile(), StandardCharsets.UTF_8), programmingExercise);
            }
            catch (IOException e) {
                log.warn("Could not read build plan file. Continue importing the exercise but skipping the build plan.", e);
            }
        }
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
     * Imports the repositories from the extracted zip file.
     *
     * @param newExercise the new programming exercise to which the repositories should be imported
     * @param basePath    the path to the extracted zip file
     * @param user        the user performing the import
     */
    private void importRepositoriesFromFile(ProgrammingExercise newExercise, Path basePath, User user) throws IOException, GitAPIException {
        Repository templateRepo = gitService.getOrCheckoutRepository(new LocalVCRepositoryUri(newExercise.getTemplateRepositoryUri()), false, true);
        Repository solutionRepo = gitService.getOrCheckoutRepository(new LocalVCRepositoryUri(newExercise.getSolutionRepositoryUri()), false, true);
        Repository testRepo = gitService.getOrCheckoutRepository(new LocalVCRepositoryUri(newExercise.getTestRepositoryUri()), false, true);
        List<Repository> auxiliaryRepositories = new ArrayList<>();
        for (AuxiliaryRepository auxiliaryRepository : newExercise.getAuxiliaryRepositories()) {
            auxiliaryRepositories.add(gitService.getOrCheckoutRepository(auxiliaryRepository.getVcsRepositoryUri(), false, true));
        }

        copyImportedExerciseContentToRepositories(templateRepo, solutionRepo, testRepo, auxiliaryRepositories, basePath);

        gitService.stageAllChanges(templateRepo);
        gitService.stageAllChanges(solutionRepo);
        gitService.stageAllChanges(testRepo);
        for (Repository auxRepo : auxiliaryRepositories) {
            gitService.stageAllChanges(auxRepo);
        }

        gitService.commitAndPush(templateRepo, "Import template from file", true, user);
        gitService.commitAndPush(solutionRepo, "Import solution from file", true, user);
        gitService.commitAndPush(testRepo, "Import tests from file", true, user);
        for (Repository auxRepo : auxiliaryRepositories) {
            gitService.commitAndPush(auxRepo, "Import auxiliary repo from file", true, user);
        }

    }

    private void copyImportedExerciseContentToRepositories(Repository templateRepo, Repository solutionRepo, Repository testRepo, List<Repository> auxiliaryRepositories,
            Path basePath) throws IOException {
        repositoryService.deleteAllContentInRepository(templateRepo);
        repositoryService.deleteAllContentInRepository(solutionRepo);
        repositoryService.deleteAllContentInRepository(testRepo);
        for (Repository auxRepo : auxiliaryRepositories) {
            repositoryService.deleteAllContentInRepository(auxRepo);
        }

        copyExerciseContentToRepository(templateRepo, RepositoryType.TEMPLATE.getName(), basePath);
        copyExerciseContentToRepository(solutionRepo, RepositoryType.SOLUTION.getName(), basePath);
        copyExerciseContentToRepository(testRepo, RepositoryType.TESTS.getName(), basePath);
        for (Repository auxRepo : auxiliaryRepositories) {
            String[] parts = auxRepo.getLocalPath().toString().split("-");
            var auxRepoName = String.join("-", Arrays.copyOfRange(parts, 1, parts.length));
            copyExerciseContentToRepository(auxRepo, auxRepoName, basePath);
        }
    }

    /**
     * Copies everything from the extracted zip file to the repository, except the .git folder
     *
     * @param repository the repository to which the content should be copied
     * @param repoName   the name of the repository
     * @param basePath   the path to the extracted zip file
     */
    private void copyExerciseContentToRepository(Repository repository, String repoName, Path basePath) throws IOException {
        // @formatter:off
        FileUtils.copyDirectory(
            retrieveRepositoryDirectoryPath(basePath, repoName).toFile(),
            repository.getLocalPath().toFile(),
            new NotFileFilter(new NameFileFilter(".git"))
        );
        // @formatter:on

        try (var files = Files.walk(repository.getLocalPath())) {
            files.filter(file -> "gradlew".equals(file.getFileName().toString())).forEach(file -> file.toFile().setExecutable(true));
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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();

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

    private Path retrieveRepositoryDirectoryPath(Path dirPath, String repoName) {
        List<Path> result;
        try (Stream<Path> walk = Files.walk(dirPath)) {
            result = walk.filter(Files::isDirectory).filter(file -> file.getFileName().toString().endsWith("-" + repoName)).toList();
        }
        catch (IOException e) {
            throw new BadRequestAlertException("Could not read the directory", "programmingExercise", "couldnotreaddirectory");
        }
        if (result.size() != 1) {
            throw new IllegalArgumentException(
                    "There are either no or more than one sub-directories containing " + repoName + " in their name. Please make sure that there is exactly one.");
        }

        return result.getFirst();
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
}
