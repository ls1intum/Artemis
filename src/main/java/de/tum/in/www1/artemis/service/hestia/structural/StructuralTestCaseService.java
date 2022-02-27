package de.tum.in.www1.artemis.service.hestia.structural;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonSyntaxException;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaType;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class StructuralTestCaseService {

    private static final Logger log = LoggerFactory.getLogger(StructuralTestCaseService.class);

    private static final String SINGLE_INDENTATION = "     ";

    private final GitService gitService;

    public StructuralTestCaseService(GitService gitService) {
        this.gitService = gitService;
    }

    private String generateCodeForClass(StructuralClass structuralClass, String[] enumValues) {
        String classSolutionCode = "";
        classSolutionCode += String.join(" ", "package", structuralClass.getPackageName()) + ";\n";
        String classModifier = structuralClass.isInterface() ? "interface" : (structuralClass.isEnum() ? "enum" : "class");
        String implementedInterfacesString = "";
        if (structuralClass.getInterfaces() != null) {
            implementedInterfacesString = "implements " + String.join(", ", structuralClass.getInterfaces()) + "";
        }
        String extendsSuperclassString = "";
        if (structuralClass.getSuperclass() != null) {
            extendsSuperclassString = "extends " + structuralClass.getSuperclass() + "";
        }
        classSolutionCode += String.join(" ", "public", classModifier, structuralClass.getName(), extendsSuperclassString, implementedInterfacesString, "{\n");
        String classInnerContent = structuralClass.isEnum() ? String.join(",\n", enumValues) : "";
        classSolutionCode += SINGLE_INDENTATION + classInnerContent + "\n}";

        return classSolutionCode;
    }

    private List<String> generateCodeForAttributes(StructuralAttribute[] attributes) {
        List<String> attributesSolutionCode = new ArrayList<>();
        if (attributes != null) {
            for (StructuralAttribute attribute : attributes) {
                String concatenatedModifiers = attribute.getModifiers() == null ? "" : String.join(" ", attribute.getModifiers());
                String result = String.join(" ", concatenatedModifiers, attribute.getType(), attribute.getName()) + ";";
                attributesSolutionCode.add(result);
            }
        }
        return attributesSolutionCode;
    }

    private List<String> generateCodeForConstructor(StructuralConstructor[] constructors, String className) {
        List<String> constructorsSolutionCode = new ArrayList<>();
        if (constructors != null) {
            for (StructuralConstructor constructor : constructors) {
                String concatenatedModifiers = constructor.getModifiers() == null ? "" : String.join(" ", constructor.getModifiers());
                String concatenatedParameters = generateArgumentsString(constructor.getParameters());
                String result = String.join(" ", concatenatedModifiers, className + concatenatedParameters, "{\n\n}").trim();
                constructorsSolutionCode.add(result);
            }
        }
        return constructorsSolutionCode;
    }

    private List<String> generateCodeForMethods(StructuralMethod[] methods) {
        List<String> methodsSolutionCode = new ArrayList<>();
        if (methods != null) {
            for (StructuralMethod method : methods) {
                String concatenatedModifiers = method.getModifiers() == null ? "" : String.join(" ", method.getModifiers());
                String concatenatedParameters = generateArgumentsString(method.getParameters());
                String result = String.join(" ", concatenatedModifiers, method.getReturnType(), method.getName() + concatenatedParameters, "{\n\n}").trim();
                methodsSolutionCode.add(result);
            }
        }

        return methodsSolutionCode;
    }

    public String generateArgumentsString(String[] parameterTypes) {
        String result = "(";
        if (parameterTypes != null) {
            for (int i = 0; i < parameterTypes.length; i++) {
                parameterTypes[i] = parameterTypes[i] + " var" + i;
            }
            result += String.join(", ", parameterTypes);
        }
        result += ")";
        return result;
    }

    public void generateStructuralSolutionEntries(ProgrammingExercise programmingExercise) throws StructuralSolutionEntryGenerationException {
        log.debug("Generating the structural SolutionEntries for the following programmingExercise: {} {}", programmingExercise.getId(), programmingExercise.getProjectName());

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

        Map<String, JavaClass> solutionClasses = getClassesFromFiles(retrieveJavaSourceFiles(solutionRepository.getLocalPath()));

        for (StructuralClassElements classElement : classElements) {
            var packageName = classElement.getStructuralClass().getPackageName();
            var name = classElement.getStructuralClass().getName();
            var solutionClass = solutionClasses.get(packageName + "." + name);
            if (solutionClass == null) {
                // TODO
            }
            String classSolutionCode = generateCodeForClass(classElement.getStructuralClass(), classElement.getEnumValues());
            List<String> constructorsSolutionCode = generateCodeForConstructor(classElement.getConstructors(), classElement.getStructuralClass().getName());
            List<String> methodsSolutionCode = generateCodeForMethods(classElement.getMethods());
            List<String> attributesSolutionCode = generateCodeForAttributes(classElement.getAttributes());
            System.out.println();
        }
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
        catch (IOException | JsonSyntaxException e) {
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
