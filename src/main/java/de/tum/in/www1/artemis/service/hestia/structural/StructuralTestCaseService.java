package de.tum.in.www1.artemis.service.hestia.structural;

import java.io.IOException;
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
import com.google.common.collect.Lists;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.*;

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

    private static final String SINGLE_INDENTATION = "    ";

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
        catch (InterruptedException | GitAPIException e) {
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
            String filePath;
            if (solutionClass != null) {
                filePath = solutionClass.getSource().getURL().getPath().replace(solutionRepository.getLocalPath().toAbsolutePath() + "/", "");
            }
            else {
                filePath = "src/" + packageName.replaceAll("\\.", "/") + "/" + name + ".java";
            }

            String classSolutionCode = generateCodeForClass(classElement.getStructuralClass(), classElement.getEnumValues(), solutionClass);
            List<String> constructorsSolutionCode = generateCodeForConstructor(classElement.getConstructors(), classElement.getStructuralClass().getName(), solutionClass);
            List<String> methodsSolutionCode = generateCodeForMethods(classElement.getMethods(), classElement.getStructuralClass(), solutionClass);
            List<String> attributesSolutionCode = generateCodeForAttributes(classElement.getAttributes(), solutionClass);
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
     * Generates well formatted the Java code for a class/interface/enum
     *
     * @param structuralClass The class object read from the test.json file
     * @param enumValues      The enum values if the class is an enum
     * @param solutionClass   The class read by QDox
     * @return The code for the class
     */
    private String generateCodeForClass(StructuralClass structuralClass, String[] enumValues, JavaClass solutionClass) {
        String classSolutionCode = "";
        classSolutionCode += String.join(" ", "package", structuralClass.getPackageName()) + ";\n";

        String genericTypes = "";
        if (solutionClass != null && !solutionClass.getTypeParameters().isEmpty()) {
            genericTypes = getGenericTypesString(solutionClass.getTypeParameters());
        }

        if (structuralClass.getModifiers() != null) {
            classSolutionCode += String.join(" ", structuralClass.getModifiers()) + " ";
        }
        classSolutionCode += (structuralClass.isInterface() ? "interface" : (structuralClass.isEnum() ? "enum" : "class")) + " ";
        classSolutionCode += structuralClass.getName() + genericTypes + " ";

        if (structuralClass.getSuperclass() != null) {
            classSolutionCode += "extends " + structuralClass.getSuperclass() + " ";
        }
        if (structuralClass.getInterfaces() != null) {
            classSolutionCode += "implements " + String.join(", ", structuralClass.getInterfaces()) + " ";
        }

        classSolutionCode += "{\n";
        String classInnerContent = structuralClass.isEnum() ? String.join(", ", enumValues) : "";
        classSolutionCode += SINGLE_INDENTATION + classInnerContent + "\n}";

        return classSolutionCode;
    }

    /**
     * Generates well formatted Java code for attributes of a class.
     *
     * @param attributes    The attribute objects read from the test.json file
     * @param solutionClass The class read by QDox that the attributes are a part of
     * @return The code for each attribute
     */
    private List<String> generateCodeForAttributes(StructuralAttribute[] attributes, JavaClass solutionClass) {
        List<String> attributesSolutionCode = new ArrayList<>();
        if (attributes == null) {
            return attributesSolutionCode;
        }

        for (StructuralAttribute attribute : attributes) {
            JavaField solutionAttribute = getSolutionAttribute(solutionClass, attribute);
            String concatenatedModifiers = formatModifiers(attribute.getModifiers());
            String result = String.join(" ", concatenatedModifiers, solutionAttribute == null ? attribute.getType() : solutionAttribute.getType().getGenericValue(),
                    attribute.getName()) + ";";
            attributesSolutionCode.add(result);
        }
        return attributesSolutionCode;
    }

    /**
     * Generates well formatted Java code for constructors of a class.
     *
     * @param constructors  The constructor objects read from the test.json file
     * @param className     The name of the class read from the test.json file
     * @param solutionClass The class read by QDox that the constructors are a part of
     * @return The code for each constructor
     */
    private List<String> generateCodeForConstructor(StructuralConstructor[] constructors, String className, JavaClass solutionClass) {
        List<String> constructorsSolutionCode = new ArrayList<>();
        if (constructors == null) {
            return constructorsSolutionCode;
        }

        for (StructuralConstructor constructor : constructors) {
            List<JavaParameter> solutionParameters = getSolutionParameters(solutionClass, constructor);
            String concatenatedModifiers = formatModifiers(constructor.getModifiers());
            String concatenatedParameters = generateParametersString(constructor.getParameters(), solutionParameters);
            String result = String.join(" ", concatenatedModifiers, className + concatenatedParameters, "{\n" + SINGLE_INDENTATION + "\n}").trim();
            constructorsSolutionCode.add(result);
        }
        return constructorsSolutionCode;
    }

    /**
     * Generates well formatted Java code for methods of a class.
     *
     * @param methods         The method objects read from the test.json file
     * @param structuralClass The class object read from the test.json file
     * @param solutionClass   The class read by QDox that the methods are a part of
     * @return The code for each method
     */
    private List<String> generateCodeForMethods(StructuralMethod[] methods, StructuralClass structuralClass, JavaClass solutionClass) {
        List<String> methodsSolutionCode = new ArrayList<>();
        if (methods == null) {
            return methodsSolutionCode;
        }

        for (StructuralMethod method : methods) {
            JavaMethod solutionMethod = getSolutionMethod(solutionClass, method);
            List<JavaParameter> solutionParameters = solutionMethod == null ? Collections.emptyList() : solutionMethod.getParameters();
            String genericTypes = "";
            if (solutionMethod != null && !solutionMethod.getTypeParameters().isEmpty()) {
                genericTypes = " " + getGenericTypesString(solutionMethod.getTypeParameters());
            }
            var modifiers = Lists.newArrayList(method.getModifiers());
            var isAbstract = modifiers.contains("abstract");
            // Adjust modifiers for interfaces
            if (structuralClass.isInterface()) {
                if (isAbstract) {
                    modifiers.remove("abstract");
                }
                else {
                    modifiers.add(0, "default");
                }
            }
            String concatenatedModifiers = formatModifiers(modifiers.toArray(new String[0]));
            String concatenatedParameters = generateParametersString(method.getParameters(), solutionParameters);
            String returnType = method.getReturnType();
            if (solutionMethod != null) {
                returnType = solutionMethod.getReturnType().getGenericValue();
            }
            String methodBody = " {\n" + SINGLE_INDENTATION + "\n}";
            // Remove the method body if the method is abstract
            if (isAbstract) {
                methodBody = ";";
            }
            String result = String.join(" ", concatenatedModifiers + genericTypes, returnType, method.getName() + concatenatedParameters + methodBody).trim();
            methodsSolutionCode.add(result);
        }

        return methodsSolutionCode;
    }

    /**
     * Formats the modifiers properly.
     * Currently, it only removes the 'optional: ' tags and joins them together.
     *
     * @param modifiers The modifiers array
     * @return The formatted modifiers
     */
    private String formatModifiers(String[] modifiers) {
        if (modifiers == null) {
            return "";
        }
        return Arrays.stream(modifiers).map(modifier -> modifier.replace("optional: ", "")).collect(Collectors.joining(" "));
    }

    /**
     * Creates the String representation of a generics declaration
     *
     * @param typeParameters The generic type parameters
     * @return The String representation
     */
    private String getGenericTypesString(List<JavaTypeVariable<JavaGenericDeclaration>> typeParameters) {
        return "<" + typeParameters.stream().map(JavaType::getGenericValue).map(type -> type.substring(1, type.length() - 1)).collect(Collectors.joining(", ")) + ">";
    }

    private JavaField getSolutionAttribute(JavaClass solutionClass, StructuralAttribute attribute) {
        return solutionClass == null ? null : solutionClass.getFields().stream().filter(field -> field.getName().equals(attribute.getName())).findFirst().orElse(null);
    }

    /**
     * Extracts the parameters from a constructor
     *
     * @param solutionClass The QDox class instance
     * @param constructor   The constructor specification from the test.json file
     * @return The parameters of the constructor
     */
    private List<JavaParameter> getSolutionParameters(JavaClass solutionClass, StructuralConstructor constructor) {
        List<JavaParameter> solutionParameters = Collections.emptyList();
        if (solutionClass == null) {
            return solutionParameters;
        }
        solutionParameters = solutionClass.getConstructors().stream()
                .filter(javaConstructor -> doParametersMatch(constructor.getParameters(), javaConstructor.getParameters(), solutionClass.getTypeParameters())).findFirst()
                .map(JavaExecutable::getParameters).orElse(Collections.emptyList());
        return solutionParameters;
    }

    /**
     * Finds the QDox method in a given class by its test.json specification
     *
     * @param solutionClass The QDox class instance
     * @param method        The method specification from the test.json file
     * @return The QDox method instance or null if not found
     */
    private JavaMethod getSolutionMethod(JavaClass solutionClass, StructuralMethod method) {
        if (solutionClass == null) {
            return null;
        }
        return solutionClass.getMethods().stream().filter(javaMethod -> javaMethod.getName().equals(method.getName())).filter(javaMethod -> {
            var genericTypes = new ArrayList<>(solutionClass.getTypeParameters());
            genericTypes.addAll(javaMethod.getTypeParameters());
            return doParametersMatch(method.getParameters(), javaMethod.getParameters(), genericTypes);
        }).findFirst().orElse(null);
    }

    /**
     * Checks if the parameters from the source files and those from the test.json match.
     * This is used for methods and constructor parameters.
     * Contains special handling for generics
     *
     * @param parameters          The parameters from the test.json file
     * @param solutionParameters  The parameters from the source code
     * @param genericDeclarations The current generic declarations
     * @return false if any parameter does not match
     */
    private boolean doParametersMatch(String[] parameters, List<JavaParameter> solutionParameters, List<JavaTypeVariable<JavaGenericDeclaration>> genericDeclarations) {
        if (parameters == null) {
            return solutionParameters.isEmpty();
        }
        if (parameters.length != solutionParameters.size()) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            var typeMatches = parameters[i].equals(solutionParameters.get(i).getType().getValue());
            var isGeneric = false;
            for (JavaTypeVariable<JavaGenericDeclaration> type : genericDeclarations) {
                if (type.getName().equals(solutionParameters.get(i).getType().getValue()) || (type.getName() + "[]").equals(solutionParameters.get(i).getType().getValue())) {
                    isGeneric = true;
                    break;
                }
            }
            if (!typeMatches && !isGeneric) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates the string representing the source code of a parameter list
     *
     * @param parameterTypes     The parameters from the test.json file
     * @param solutionParameters The parameters from the source code
     * @return The parameter source code
     */
    private String generateParametersString(String[] parameterTypes, List<JavaParameter> solutionParameters) {
        String result = "(";
        if (parameterTypes != null) {
            for (int i = 0; i < parameterTypes.length; i++) {
                if (solutionParameters.size() > i) {
                    // Use original parameter names
                    parameterTypes[i] = solutionParameters.get(i).getType().getGenericValue() + " " + solutionParameters.get(i).getName();
                }
                else {
                    // Use var[i] as a fallback
                    parameterTypes[i] = parameterTypes[i] + " var" + i;
                }
            }
            result += String.join(", ", parameterTypes);
        }
        result += ")";
        return result;
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
