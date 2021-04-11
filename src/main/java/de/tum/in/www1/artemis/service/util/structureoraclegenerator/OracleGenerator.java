package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;

import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * This generator is used to parse the structure of a programming exercise to be then assessed from Artemis.
 * It is used to automatically generate the structure oracle with the solution of the exercise as the system model and the template as the test model.
 * The oracle is saved in the form of a JSON file in test.json.
 * The structure oracle is used in the structural tests and contains information on the expected structural elements that the student has to implement.
 * The generator uses the qdox framework (https://github.com/paul-hammant/qdox).
 * It extracts first the needed elements by doing a so-called diff of each element e.g. the difference between the solution of an exercise and its template.
 * The generator uses separate data structures that contain the elements of these diffs and then creates JSON representations of them.
 *
 * The generator currently deals with the following structural elements:
 * <ul>
 *     <li>Class hierarchy: abstract modifier, stereotype, declared super classes and implemented interfaces.</li>
 *     <li>Attributes: name, type and visibility modifier.</li>
 *     <li>Constructor: parameter types and visibility modifier.</li>
 *     <li>Methods: name, parameter types, return type and also visibility modifier. These basic elements get aggregated
 *          into types and their children classes.</li>
 *     <li>Types: class hierarchy and methods.</li>
 *     <li>Interfaces: the elements in the types.</li>
 *     <li>Classes: the elements in the types as well as attributes and constructors.</li>
 *     <li>Enums: the elements in the classes as well as enum values.</li>
 * </ul>
 *
 * The steps the oracle generator takes are the following:
 * <ol>
 *     <li>Feed the Spoon Framework the path to the projects of the solution and template projects and also the path
 *          where the oracle file needs to be saved.</li>
 *     <li>Extract all the types (and classes, enums, interfaces) from the meta models of the projects using the Spoon Framework.</li>
 *     <li>Create pairs of homologous types from the types in the solution and their corresponding counterparts in the template.</li>
 *     <li>Compute the diff for each pair of types and for each structural element contained in the types,
 *          e.g. for each of the structural elements described above check here which ones are present in the solution
 *          code and not in the template code.</li>
 *    <li>Generate the JSON representation for each diff.</li>
 *    <li>Assemble the JSON objects into a JSON array of all the types of the structure diff.</li>
 * </ol>
 */
public class OracleGenerator {

    private static final Logger log = LoggerFactory.getLogger(OracleGenerator.class);

    /**
     * This method generates the structure oracle by scanning the Java projects contained in the paths passed as arguments.
     *
     * @param solutionProjectPath The path to the project of the solution of a programming exercise.
     * @param templateProjectPath The path to the project of the template of a programming exercise.
     * @return The string of the JSON representation of the structure oracle.
     */
    public static String generateStructureOracleJSON(Path solutionProjectPath, Path templateProjectPath) {
        log.debug("Generating the Oracle for the following projects:\nSolution project: {}\nTemplate project: {}\n", solutionProjectPath, templateProjectPath);

        // Initialize the empty string.
        JsonArray structureOracleJSON = new JsonArray();

        // Generate the pairs of the types found in the solution project with the corresponding one from the template project.
        Map<JavaClass, JavaClass> solutionToTemplateMapping = generateSolutionToTemplateMapping(solutionProjectPath, templateProjectPath);

        // Loop over each pair of types and create the diff data structures and the JSON representation afterwards for each.
        // If the types, classes or enums are equal, then ignore and continue with the next pair
        for (Map.Entry<JavaClass, JavaClass> entry : solutionToTemplateMapping.entrySet()) {
            JsonObject diffJSON = new JsonObject();
            JavaClass solutionType = entry.getKey();
            JavaClass templateType = entry.getValue();

            // Initialize the types diff containing various properties as well as methods.
            JavaClassDiff javaClassDiff = new JavaClassDiff(solutionType, templateType);
            if (javaClassDiff.classesAreEqual()) {
                continue;
            }

            // If we are dealing with interfaces, the types diff already has all the information we need
            // So we do not need to do anything more
            JavaClassDiffSerializer serializer = new JavaClassDiffSerializer(javaClassDiff);
            diffJSON.add("class", serializer.serializeClassProperties());
            if (!javaClassDiff.methodsDiff.isEmpty()) {
                diffJSON.add("methods", serializer.serializeMethods());
            }

            if (!javaClassDiff.attributesDiff.isEmpty()) {
                diffJSON.add("attributes", serializer.serializeAttributes());
            }

            if (!javaClassDiff.enumsDiff.isEmpty()) {
                diffJSON.add("enumValues", serializer.serializeEnums());
            }

            if (!javaClassDiff.constructorsDiff.isEmpty()) {
                diffJSON.add("constructors", serializer.serializeConstructors());
            }

            log.debug("Generated JSON for '{}'.", solutionType.getCanonicalName());
            structureOracleJSON.add(diffJSON);
        }

        return prettyPrint(structureOracleJSON);
    }

    /**
     * This method pretty prints a given JSON array.
     *
     * @param jsonArray The JSON array that needs to get pretty printed.
     * @return The pretty printed JSON array in its string representation. If there is any IO exception, an empty string is returned instead.
     */
    private static String prettyPrint(JsonArray jsonArray) {
        ObjectMapper mapper = new ObjectMapper();

        // TODO: instead of using two different libraries and convert the json vice versa we should try to pretty print via gson or directly use Jackson
        try {
            Object jsonObject = mapper.readValue(jsonArray.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        }
        catch (IOException e) {
            log.error("Could not pretty print the JSON!", e);
            throw new InternalServerErrorException("Could not pretty print the JSON!");
        }
    }

    /**
     * This method scans the meta models of the solution and template projects and generates pairs of matching types. Matching types means here types with the same name, since they
     * are uniquely defined from their names. The list of the type pairs contains with certainty all the types found in the solution project, but it often happens that no
     * corresponding types are declared in the template. For this, a null type is inserted instead and handled accordingly in the diffs. Also, if the type in the template is the
     * same to the one in the solution, then they get ignored and do not get added to the structure oracle.
     *
     * @param solutionProjectPath The path to the solution project.
     * @param templateProjectPath The path to the template project.
     * @return A hash map containing the type pairs of the solution types and their respective counterparts in the template.
     */
    private static Map<JavaClass, JavaClass> generateSolutionToTemplateMapping(Path solutionProjectPath, Path templateProjectPath) {
        List<File> templateFiles = retrieveJavaSourceFiles(templateProjectPath);
        List<File> solutionFiles = retrieveJavaSourceFiles(solutionProjectPath);
        log.debug("Template Java Files {}", templateFiles);
        log.debug("Solution Java Files {}", solutionFiles);
        List<JavaClass> templateClasses = getClassesFromFiles(templateFiles);
        List<JavaClass> solutionClasses = getClassesFromFiles(solutionFiles);

        Map<JavaClass, JavaClass> solutionToTemplateMapping = new HashMap<>();

        for (JavaClass solutionClass : solutionClasses) {
            // Put an empty template class as a default placeholder.
            solutionToTemplateMapping.put(solutionClass, null);

            for (JavaClass templateClass : templateClasses) {
                if (solutionClass.getSimpleName().equals(templateClass.getSimpleName()) && templateClass.getPackageName().equals(solutionClass.getPackageName())) {
                    // If a template class with the same name and package gets found, then replace the empty template with the real one.
                    solutionToTemplateMapping.put(solutionClass, templateClass);
                    break;
                }
            }
        }

        return solutionToTemplateMapping;
    }

    private static List<JavaClass> getClassesFromFiles(List<File> javaSourceFiles) {

        List<JavaClass> foundJavaClasses = new ArrayList<>();
        for (File javaSourceFile : javaSourceFiles) {
            try {
                JavaProjectBuilder builder = new JavaProjectBuilder();
                JavaSource src = builder.addSource(javaSourceFile);
                foundJavaClasses.addAll(src.getClasses());
            }
            catch (IOException e) {
                log.error("Could not add java source to builder", e);
            }
        }
        return foundJavaClasses;
    }

    private static List<File> retrieveJavaSourceFiles(Path path) {
        List<File> foundFiles = new ArrayList<>();

        walkProjectFileStructure(path.toFile(), foundFiles);

        return foundFiles;
    }

    private static void walkProjectFileStructure(File file, List<File> foundFiles) {
        String fileName = file.getName();

        if (fileName.contains(".java")) {
            foundFiles.add(file);
        }

        if (file.isDirectory()) {
            String[] subFiles = file.list();
            if (subFiles != null) {
                for (String subFile : subFiles) {
                    walkProjectFileStructure(new File(file, subFile), foundFiles);
                }
            }
        }
    }

}
