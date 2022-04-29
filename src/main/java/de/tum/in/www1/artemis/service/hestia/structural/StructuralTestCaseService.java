package de.tum.in.www1.artemis.service.hestia.structural;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaType;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;

/**
 * Service for handling Solution Entries of structural Test Cases.
 */
@Service
public class StructuralTestCaseService {

    private final Logger log = LoggerFactory.getLogger(StructuralTestCaseService.class);

    private final GitService gitService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public StructuralTestCaseService(GitService gitService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseSolutionEntryRepository solutionEntryRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository) {
        this.gitService = gitService;
        this.testCaseRepository = testCaseRepository;
        this.solutionEntryRepository = solutionEntryRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
    }

    /**
     * Generates the solution entries for all structural test cases of a programming exercise.
     * This includes solution entries for classes, attributes, methods and constructors.
     *
     * @param programmingExercise The programming exercise
     * @return The new structural solution entries
     * @throws StructuralSolutionEntryGenerationException If there was an error while generating the solution entries
     */
    public List<ProgrammingExerciseSolutionEntry> generateStructuralSolutionEntries(ProgrammingExercise programmingExercise) throws StructuralSolutionEntryGenerationException {
        log.debug("Generating the structural SolutionEntries for the following programmingExercise: {} {}", programmingExercise.getId(), programmingExercise.getProjectName());

        var testCases = testCaseRepository.findByExerciseIdWithSolutionEntriesAndActive(programmingExercise.getId(), true);
        testCases.removeIf(testCase -> testCase.getType() != ProgrammingExerciseTestCaseType.STRUCTURAL);

        // No test cases = no solution entries needed
        if (testCases.isEmpty()) {
            return Collections.emptyList();
        }

        // Checkout the solution and test repositories
        Repository solutionRepository;
        Repository testRepository;
        try {
            var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
            if (solutionParticipation.isEmpty()) {
                return Collections.emptyList();
            }
            solutionRepository = gitService.getOrCheckoutRepository(solutionParticipation.get().getVcsRepositoryUrl(), true);
            testRepository = gitService.getOrCheckoutRepository(programmingExercise.getVcsTestRepositoryUrl(), true);

            gitService.resetToOriginHead(solutionRepository);
            gitService.pullIgnoreConflicts(solutionRepository);
            gitService.resetToOriginHead(testRepository);
            gitService.pullIgnoreConflicts(testRepository);
        }
        catch (GitAPIException e) {
            var error = "Error while checking out repositories";
            log.error(error, e);
            throw new StructuralSolutionEntryGenerationException(error, e);
        }

        var classElements = readStructureOracleFile(testRepository.getLocalPath());
        var solutionClasses = getClassesFromFiles(retrieveJavaSourceFiles(solutionRepository.getLocalPath()));

        // Create new solution entries
        List<ProgrammingExerciseSolutionEntry> newSolutionEntries = generateStructuralSolutionEntries(testCases, solutionRepository, classElements, solutionClasses);

        // Get all old solution entries
        var oldSolutionEntries = newSolutionEntries.stream().map(ProgrammingExerciseSolutionEntry::getTestCase).flatMap(testCase -> testCase.getSolutionEntries().stream())
                .distinct().toList();

        // Save new solution entries
        newSolutionEntries = solutionEntryRepository.saveAll(newSolutionEntries);

        // Delete old solution entries
        solutionEntryRepository.deleteAll(oldSolutionEntries);

        return newSolutionEntries;
    }

    /**
     * Private method that takes care of the actual generation of structural solution entries.
     *
     * @param testCases          The test cases of the programming exercise
     * @param solutionRepository The solution repository of the programming exercise
     * @param classElements      The entries from the test.json file
     * @param solutionClasses    The classes read with QDox
     * @return The new structural solution entries
     */
    private List<ProgrammingExerciseSolutionEntry> generateStructuralSolutionEntries(Set<ProgrammingExerciseTestCase> testCases, Repository solutionRepository,
            StructuralClassElements[] classElements, Map<String, JavaClass> solutionClasses) {
        return Arrays.stream(classElements).flatMap(classElement -> {
            var packageName = classElement.getStructuralClass().getPackageName();
            var name = classElement.getStructuralClass().getName();
            var solutionClass = solutionClasses.get(packageName + "." + name);
            String filePath = "src/" + packageName.replaceAll("\\.", "/") + "/" + name + ".java";
            if (solutionClass != null) {
                try {
                    filePath = solutionRepository.getLocalPath().toAbsolutePath().toUri().relativize(solutionClass.getSource().getURL().toURI()).toString();
                }
                catch (URISyntaxException e) {
                    log.warn("Unable to create file path for class", e);
                }
            }

            String classSolutionCode = classElement.getStructuralClass().getSourceCode(classElement, solutionClass);
            List<String> constructorsSolutionCode = classElement.getConstructors().stream().map(constructor -> constructor.getSourceCode(classElement, solutionClass)).toList();
            List<String> methodsSolutionCode = classElement.getMethods().stream().map(method -> method.getSourceCode(classElement, solutionClass)).toList();
            List<String> attributesSolutionCode = classElement.getAttributes().stream().map(attribute -> attribute.getSourceCode(classElement, solutionClass)).toList();
            return Stream.of(createSolutionEntry(filePath, classSolutionCode, findStructuralTestCase("Class", name, testCases)),
                    createSolutionEntry(filePath, String.join("\n\n", attributesSolutionCode), findStructuralTestCase("Attributes", name, testCases)),
                    createSolutionEntry(filePath, String.join("\n\n", constructorsSolutionCode), findStructuralTestCase("Constructors", name, testCases)),
                    createSolutionEntry(filePath, String.join("\n\n", methodsSolutionCode), findStructuralTestCase("Methods", name, testCases)));
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Finds a structural test case of a specific type and class in the list of all test cases of an exercise.
     *
     * @param type      The type of the structural test case (e.g. Class, Methods)
     * @param className The name of the Class
     * @param testCases The list of test cases
     * @return The matching test case or empty in none found
     */
    private Optional<ProgrammingExerciseTestCase> findStructuralTestCase(String type, String className, Set<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().filter(testCase -> testCase.getTestName().equals("test" + type + "[" + className + "]")).findFirst();
    }

    /**
     * Helper method for creating a solution entry.
     * If the given test case is not present this will return null.
     *
     * @param filePath The filePath of the solution entry
     * @param code     The code of the solution entry
     * @param testCase The test case of the solution entry
     * @return A SolutionEntry if testCase is present otherwise null
     */
    private ProgrammingExerciseSolutionEntry createSolutionEntry(String filePath, String code, Optional<ProgrammingExerciseTestCase> testCase) {
        return testCase.map(actualTestCase -> {
            var solutionEntry = new ProgrammingExerciseSolutionEntry();
            solutionEntry.setFilePath(filePath);
            solutionEntry.setPreviousLine(null);
            solutionEntry.setPreviousCode(null);
            solutionEntry.setLine(1);
            solutionEntry.setCode(code);
            solutionEntry.setTestCase(actualTestCase);
            return solutionEntry;
        }).orElse(null);
    }

    /**
     * Finds, reads and parses the test.json file from the test repository
     *
     * @param testRepoPath The base path of the test repository
     * @return The parsed test.json file
     * @throws StructuralSolutionEntryGenerationException If the test.json does not exist or could not be read
     */
    private StructuralClassElements[] readStructureOracleFile(Path testRepoPath) throws StructuralSolutionEntryGenerationException {
        try (Stream<Path> files = Files.walk(testRepoPath)) {
            var testJsonFile = files.filter(Files::isRegularFile).filter(path -> "test.json".equals(path.getFileName().toString())).findFirst();
            if (testJsonFile.isEmpty()) {
                throw new StructuralSolutionEntryGenerationException("Unable to locate test.json");
            }
            else {
                String jsonContent = Files.readString(testJsonFile.get());
                var objectMapper = new ObjectMapper();
                return objectMapper.readValue(jsonContent, StructuralClassElements[].class);
            }
        }
        catch (IOException e) {
            throw new StructuralSolutionEntryGenerationException("Error while reading test.json", e);
        }
    }

    /**
     * Collects all java source files in a given path.
     *
     * @param start The base path
     * @return The paths to all java source files
     * @throws StructuralSolutionEntryGenerationException If there was an IOException
     */
    private List<Path> retrieveJavaSourceFiles(Path start) throws StructuralSolutionEntryGenerationException {
        var matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
        try (Stream<Path> files = Files.walk(start)) {
            return files.filter(Files::isRegularFile).filter(matcher::matches).toList();
        }
        catch (IOException e) {
            var error = "Could not retrieve the project files to generate the structural solution entries";
            log.error(error, e);
            throw new StructuralSolutionEntryGenerationException(error, e);
        }
    }

    private Map<String, JavaClass> getClassesFromFiles(List<Path> javaSourceFiles) throws StructuralSolutionEntryGenerationException {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        try {
            for (Path source : javaSourceFiles) {
                builder.addSource(source.toFile());
            }
        }
        catch (IOException e) {
            var error = "Could not add java source to builder";
            log.error(error, e);
            throw new StructuralSolutionEntryGenerationException(error, e);
        }
        return builder.getClasses().stream().collect(Collectors.toMap(JavaType::getFullyQualifiedName, clazz -> clazz));
    }
}
