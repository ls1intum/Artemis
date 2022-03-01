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
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class StructuralTestCaseService {

    private static final Logger log = LoggerFactory.getLogger(StructuralTestCaseService.class);

    private static final String SINGLE_INDENTATION = "    ";

    private final GitService gitService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    public StructuralTestCaseService(GitService gitService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseSolutionEntryRepository solutionEntryRepository) {
        this.gitService = gitService;
        this.testCaseRepository = testCaseRepository;
        this.solutionEntryRepository = solutionEntryRepository;
    }

    /**
     * Generates the solution entries for all structural test cases of a programming exercise.
     * This includes solution entries for classes, attributes, methods and constructors.
     *
     * @param programmingExercise The programming exercise
     * @throws StructuralSolutionEntryGenerationException If there was an error while generating the solution entries
     * @return The new structural solution entries
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
            solutionRepository = gitService.getOrCheckoutRepository(programmingExercise.getVcsSolutionRepositoryUrl(), true);
            testRepository = gitService.getOrCheckoutRepository(programmingExercise.getVcsTestRepositoryUrl(), true);
        }
        catch (InterruptedException | GitAPIException e) {
            var error = "Error while checking out repositories";
            log.error(error, e);
            throw new StructuralSolutionEntryGenerationException(error, e);
        }

        var classElements = readStructureOracleFile(testRepository.getLocalPath());
        var solutionClasses = getClassesFromFiles(retrieveJavaSourceFiles(solutionRepository.getLocalPath()));

        // Create new solution entries
        var newSolutionEntries = Arrays.stream(classElements).flatMap(classElement -> {
            var packageName = classElement.getStructuralClass().getPackageName();
            var name = classElement.getStructuralClass().getName();
            var solutionClass = solutionClasses.get(packageName + "." + name);
            var filePath = solutionClass.getSource().getURL().getPath().replace(solutionRepository.getLocalPath().toAbsolutePath().toString() + "/", "");

            String classSolutionCode = generateCodeForClass(classElement.getStructuralClass(), classElement.getEnumValues(), solutionClass);
            List<String> constructorsSolutionCode = generateCodeForConstructor(classElement.getConstructors(), classElement.getStructuralClass().getName(), solutionClass);
            List<String> methodsSolutionCode = generateCodeForMethods(classElement.getMethods(), solutionClass);
            List<String> attributesSolutionCode = generateCodeForAttributes(classElement.getAttributes(), solutionClass);
            return Stream.of(createSolutionEntry(filePath, classSolutionCode, findStructuralTestCase("Class", name, testCases)),
                    createSolutionEntry(filePath, String.join("\n\n", attributesSolutionCode), findStructuralTestCase("Attributes", name, testCases)),
                    createSolutionEntry(filePath, String.join("\n\n", constructorsSolutionCode), findStructuralTestCase("Constructors", name, testCases)),
                    createSolutionEntry(filePath, String.join("\n\n", methodsSolutionCode), findStructuralTestCase("Methods", name, testCases)));
        }).filter(Objects::nonNull).collect(Collectors.toList());

        // Get all old solution entries
        var oldSolutionEntries = newSolutionEntries.stream().map(ProgrammingExerciseSolutionEntry::getTestCase).flatMap(testCase -> testCase.getSolutionEntries().stream())
                .distinct().toList();

        // Save new solution entries
        newSolutionEntries = solutionEntryRepository.saveAll(newSolutionEntries);

        // Delete old solution entries
        solutionEntryRepository.deleteAll(oldSolutionEntries);

        return newSolutionEntries;
    }

    private Optional<ProgrammingExerciseTestCase> findStructuralTestCase(String type, String className, Set<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().filter(testCase -> testCase.getTestName().equals("test" + type + "[" + className + "]")).findFirst();
    }

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

    private String generateCodeForClass(StructuralClass structuralClass, String[] enumValues, JavaClass solutionClass) {
        String classSolutionCode = "";
        classSolutionCode += String.join(" ", "package", structuralClass.getPackageName()) + ";\n";
        String classModifier = structuralClass.isInterface() ? "interface" : (structuralClass.isEnum() ? "enum" : "class");

        String genericTypes = "";
        if (solutionClass != null && !solutionClass.getTypeParameters().isEmpty()) {
            genericTypes = getGenericTypesString(solutionClass.getTypeParameters());
        }

        String implementedInterfacesString = "";
        if (structuralClass.getInterfaces() != null) {
            implementedInterfacesString = "implements " + String.join(", ", structuralClass.getInterfaces()) + "";
        }

        String extendsSuperclassString = "";
        if (structuralClass.getSuperclass() != null) {
            extendsSuperclassString = "extends " + structuralClass.getSuperclass() + "";
        }

        classSolutionCode += String.join(" ", "public", classModifier, structuralClass.getName() + genericTypes, extendsSuperclassString, implementedInterfacesString, "{\n");
        String classInnerContent = structuralClass.isEnum() ? String.join(",\n", enumValues) : "";
        classSolutionCode += SINGLE_INDENTATION + classInnerContent + "\n}";

        return classSolutionCode;
    }

    private List<String> generateCodeForAttributes(StructuralAttribute[] attributes, JavaClass solutionClass) {
        List<String> attributesSolutionCode = new ArrayList<>();
        if (attributes != null) {
            for (StructuralAttribute attribute : attributes) {
                JavaField solutionAttribute = getSolutionAttribute(solutionClass, attribute);
                String concatenatedModifiers = attribute.getModifiers() == null ? "" : String.join(" ", attribute.getModifiers());
                String result = String.join(" ", concatenatedModifiers, solutionAttribute == null ? attribute.getType() : solutionAttribute.getType().getGenericValue(),
                        attribute.getName()) + ";";
                attributesSolutionCode.add(result);
            }
        }
        return attributesSolutionCode;
    }

    private List<String> generateCodeForConstructor(StructuralConstructor[] constructors, String className, JavaClass solutionClass) {
        List<String> constructorsSolutionCode = new ArrayList<>();
        if (constructors == null) {
            return constructorsSolutionCode;
        }
        for (StructuralConstructor constructor : constructors) {
            List<JavaParameter> solutionParameters = getSolutionParameters(solutionClass, constructor);
            String concatenatedModifiers = constructor.getModifiers() == null ? "" : String.join(" ", constructor.getModifiers());
            String concatenatedParameters = generateArgumentsString(constructor.getParameters(), solutionParameters);
            String result = String.join(" ", concatenatedModifiers, className + concatenatedParameters, "{\n\n}").trim();
            constructorsSolutionCode.add(result);
        }
        return constructorsSolutionCode;
    }

    private List<String> generateCodeForMethods(StructuralMethod[] methods, JavaClass solutionClass) {
        List<String> methodsSolutionCode = new ArrayList<>();
        if (methods != null) {
            for (StructuralMethod method : methods) {
                JavaMethod solutionMethod = getSolutionMethod(solutionClass, method);
                List<JavaParameter> solutionParameters = solutionMethod == null ? Collections.emptyList() : solutionMethod.getParameters();
                String genericTypes = "";
                if (solutionMethod != null && !solutionMethod.getTypeParameters().isEmpty()) {
                    genericTypes = getGenericTypesString(solutionMethod.getTypeParameters());
                }
                String concatenatedModifiers = method.getModifiers() == null ? "" : String.join(" ", method.getModifiers());
                String concatenatedParameters = generateArgumentsString(method.getParameters(), solutionParameters);
                String returnType = method.getReturnType();
                if (solutionMethod != null) {
                    returnType = solutionMethod.getReturnType().getGenericValue();
                }
                String result = String.join(" ", concatenatedModifiers + genericTypes, returnType, method.getName() + concatenatedParameters, "{\n\n}").trim();
                methodsSolutionCode.add(result);
            }
        }

        return methodsSolutionCode;
    }

    private String getGenericTypesString(List<JavaTypeVariable<JavaGenericDeclaration>> typeParameters) {
        return " <" + typeParameters.stream().map(JavaType::getGenericValue).map(type -> type.substring(1, type.length() - 1)).collect(Collectors.joining(", ")) + ">";
    }

    private JavaField getSolutionAttribute(JavaClass solutionClass, StructuralAttribute attribute) {
        return solutionClass.getFields().stream().filter(field -> field.getName().equals(attribute.getName())).findFirst().orElse(null);
    }

    private List<JavaParameter> getSolutionParameters(JavaClass solutionClass, StructuralConstructor constructor) {
        List<JavaParameter> solutionParameters = Collections.emptyList();
        if (solutionClass != null) {
            solutionParameters = solutionClass.getConstructors().stream()
                    .filter(javaConstructor -> doParametersMatch(constructor.getParameters(), javaConstructor.getParameters(), solutionClass.getTypeParameters())).findFirst()
                    .map(JavaExecutable::getParameters).orElse(Collections.emptyList());
        }
        return solutionParameters;
    }

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

    private boolean doParametersMatch(String[] parameters, List<JavaParameter> solutionParameters, List<JavaTypeVariable<JavaGenericDeclaration>> genericDeclarations) {
        if (parameters == null) {
            return solutionParameters.size() == 0;
        }
        if (parameters.length != solutionParameters.size()) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            var typeMatches = parameters[i].equals(solutionParameters.get(i).getType().getValue());
            var isGeneric = false;
            for (JavaTypeVariable<JavaGenericDeclaration> type : genericDeclarations) {
                if (type.getName().equals(solutionParameters.get(i).getType().getValue())) {
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

    private String generateArgumentsString(String[] parameterTypes, List<JavaParameter> solutionParameters) {
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

    private StructuralClassElements[] readStructureOracleFile(Path testRepoPath) throws StructuralSolutionEntryGenerationException {
        try {
            var testJsonFile = Files.walk(testRepoPath).filter(Files::isRegularFile).filter(path -> "test.json".equals(path.getFileName().toString())).findFirst();
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

    private List<Path> retrieveJavaSourceFiles(Path start) throws StructuralSolutionEntryGenerationException {
        var matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
        try {
            return Files.walk(start).filter(Files::isRegularFile).filter(matcher::matches).toList();
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
