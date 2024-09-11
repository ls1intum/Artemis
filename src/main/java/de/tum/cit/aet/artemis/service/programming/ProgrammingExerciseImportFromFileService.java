package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.export.ProgrammingExerciseExportService.BUILD_PLAN_FILE_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.service.ZipFileService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseImportFromFileService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseImportFromFileService.class);

    private final ProgrammingExerciseService programmingExerciseService;

    private final ZipFileService zipFileService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final RepositoryService repositoryService;

    private final GitService gitService;

    private final FileService fileService;

    private final ProfileService profileService;

    private final BuildPlanRepository buildPlanRepository;

    private static final List<String> SHORT_NAME_REPLACEMENT_EXCLUSIONS = List.of("gradle-wrapper.jar");

    public ProgrammingExerciseImportFromFileService(ProgrammingExerciseService programmingExerciseService, ZipFileService zipFileService,
            StaticCodeAnalysisService staticCodeAnalysisService, RepositoryService repositoryService, GitService gitService, FileService fileService, ProfileService profileService,
            BuildPlanRepository buildPlanRepository) {
        this.programmingExerciseService = programmingExerciseService;
        this.zipFileService = zipFileService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
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
     * @return the imported programming exercise
     **/
    public ProgrammingExercise importProgrammingExerciseFromFile(ProgrammingExercise originalProgrammingExercise, MultipartFile zipFile, Course course, User user)
            throws IOException, GitAPIException, URISyntaxException {
        if (!"zip".equals(FilenameUtils.getExtension(zipFile.getOriginalFilename()))) {
            throw new BadRequestAlertException("The file is not a zip file", "programmingExercise", "fileNotZip");
        }
        Path importExerciseDir = null;
        ProgrammingExercise newProgrammingExercise;
        try {
            importExerciseDir = Files.createTempDirectory("imported-exercise-dir");
            Path exerciseFilePath = Files.createTempFile(importExerciseDir, "exercise-for-import", ".zip");

            zipFile.transferTo(exerciseFilePath);
            zipFileService.extractZipFileRecursively(exerciseFilePath);
            checkRepositoriesExist(importExerciseDir);
            var oldShortName = getProgrammingExerciseFromDetailsFile(importExerciseDir).getShortName();
            programmingExerciseService.validateNewProgrammingExerciseSettings(originalProgrammingExercise, course);
            // TODO: creating the whole exercise (from template) is a bad solution in this case, we do not want the template content, instead we want the file content of the zip
            newProgrammingExercise = programmingExerciseService.createProgrammingExercise(originalProgrammingExercise, true);
            if (Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled())) {
                staticCodeAnalysisService.createDefaultCategories(newProgrammingExercise);
            }
            Path pathToDirectoryWithImportedContent = exerciseFilePath.toAbsolutePath().getParent().resolve(FilenameUtils.getBaseName(exerciseFilePath.toString()));
            copyEmbeddedFiles(pathToDirectoryWithImportedContent);
            importRepositoriesFromFile(newProgrammingExercise, importExerciseDir, oldShortName, user);
            newProgrammingExercise.setCourse(course);
            // It doesn't make sense to import a build plan on a local CI setup.
            if (profileService.isGitlabCiOrJenkinsActive()) {
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
                Path targetPath = FilePathService.getMarkdownFilePath().resolve(file.getFileName());
                if (!Files.exists(targetPath)) {
                    FileUtils.copyFile(file.toFile(), targetPath.toFile());
                }
            }
        }
    }

    private void importRepositoriesFromFile(ProgrammingExercise newExercise, Path basePath, String oldExerciseShortName, User user)
            throws IOException, GitAPIException, URISyntaxException {
        Repository templateRepo = gitService.getOrCheckoutRepository(new VcsRepositoryUri(newExercise.getTemplateRepositoryUri()), false);
        Repository solutionRepo = gitService.getOrCheckoutRepository(new VcsRepositoryUri(newExercise.getSolutionRepositoryUri()), false);
        Repository testRepo = gitService.getOrCheckoutRepository(new VcsRepositoryUri(newExercise.getTestRepositoryUri()), false);

        copyImportedExerciseContentToRepositories(templateRepo, solutionRepo, testRepo, basePath);
        replaceImportedExerciseShortName(Map.of(oldExerciseShortName, newExercise.getShortName()), templateRepo, solutionRepo, testRepo);

        gitService.stageAllChanges(templateRepo);
        gitService.stageAllChanges(solutionRepo);
        gitService.stageAllChanges(testRepo);

        gitService.commitAndPush(templateRepo, "Import template from file", true, user);
        gitService.commitAndPush(solutionRepo, "Import solution from file", true, user);
        gitService.commitAndPush(testRepo, "Import tests from file", true, user);
    }

    private void replaceImportedExerciseShortName(Map<String, String> replacements, Repository... repositories) {
        for (Repository repository : repositories) {
            fileService.replaceVariablesInFileRecursive(repository.getLocalPath(), replacements, SHORT_NAME_REPLACEMENT_EXCLUSIONS);
        }
    }

    private void copyImportedExerciseContentToRepositories(Repository templateRepo, Repository solutionRepo, Repository testRepo, Path basePath) throws IOException {
        repositoryService.deleteAllContentInRepository(templateRepo);
        repositoryService.deleteAllContentInRepository(solutionRepo);
        repositoryService.deleteAllContentInRepository(testRepo);
        copyExerciseContentToRepository(templateRepo, RepositoryType.TEMPLATE, basePath);
        copyExerciseContentToRepository(solutionRepo, RepositoryType.SOLUTION, basePath);
        copyExerciseContentToRepository(testRepo, RepositoryType.TESTS, basePath);
    }

    /**
     * Copies everything from the extracted zip file to the repository, except the .git folder
     *
     * @param repository     the repository to which the content should be copied
     * @param repositoryType the type of the repository
     * @param basePath       the path to the extracted zip file
     **/
    private void copyExerciseContentToRepository(Repository repository, RepositoryType repositoryType, Path basePath) throws IOException {
        FileUtils.copyDirectory(retrieveRepositoryDirectoryPath(basePath, repositoryType.getName()).toFile(), repository.getLocalPath().toFile(),
                new NotFileFilter(new NameFileFilter(".git")));
        try (var files = Files.walk(repository.getLocalPath())) {
            files.filter(file -> "gradlew".equals(file.getFileName().toString())).forEach(file -> file.toFile().setExecutable(true));
        }
    }

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

    private Path retrieveRepositoryDirectoryPath(Path dirPath, String repoType) {
        List<Path> result;
        try (Stream<Path> walk = Files.walk(dirPath)) {
            result = walk.filter(Files::isDirectory).filter(file -> file.getFileName().toString().endsWith("-" + repoType)).toList();
        }
        catch (IOException e) {
            throw new BadRequestAlertException("Could not read the directory", "programmingExercise", "couldnotreaddirectory");
        }
        if (result.size() != 1) {
            throw new IllegalArgumentException(
                    "There are either no or more than one sub-directories containing " + repoType + " in their name. Please make sure that there is exactly one.");
        }

        return result.getFirst();
    }

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
