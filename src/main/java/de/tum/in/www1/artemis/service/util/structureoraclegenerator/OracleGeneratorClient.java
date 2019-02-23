package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Kristian Dimo (kristian.dimo@tum.de)
 * 
 * This generator is used to parse the structure of a programming exercise
 * to be then assessed from ArTEMiS. It is used to automatically generate the structure oracle
 * with the solution of the exercise as the system model and the template as the test model.
 * The oracle is saved in the form of a JSON file in test.json.
 * The structure oracle is used in the structural tests and contains information on the
 * expected structural elements that the student has to implement.
 * 
 * The generator uses the Spoon framework (http://spoon.gforge.inria.fr/) from the INRIA
 * institute.
 * It extracts first the needed elemnents by doing a so-called diff of each element
 * e.g. the difference between the solution of an exercise and its template.
 * The generator uses separate data structures that contain the elements of these diffs
 * and then creates JSON representations of them.
 *
 * The generator currently deals with the following structural elements:
 * - Class hierarchy: abstract modifier, stereotype, declared super- classes and implemented interfaces.
 * - Attributes: name, type and visibility modifier.
 * - Constructor: parameter types and visibility modifier.
 * - Methods: name, parameter types, return type and also visibility modifier.
 *
 * These basic elements get aggregated into types and their children classes.
 * - Types: class hierarchy and methods.
 * - Interfaces: the elements in the types.
 * - Classes: the elements in the types as well as attributes and constructors.
 * - Enums: the elements in the classes as well as enum values.
 * 
 * The steps the oracle generator takes are the following:
 * 	1. Feed the Spoon Framework the path to the projects of the solution and
 *	 	template projects and also the path where the oracle file needs to be saved.
 * 	2. Extract all the types (and classes, enums, interfaces)
 * 		from the metamodels of the projects using the Spoon Framework.
 * 	3. Create pairs of homonymous types from the types in the solution and their corresponding
 * 	    counterparts in the template.
 * 	4. Compute the diff for each pair of types and for each structural element contained in the types,
 * 	    e.g. for each of the structural elements described above check here which ones are present
 * 	    in the solution code and not in the template code.
 * 	6. Generate the JSON representation for each diff.
 * 	7. Assemble the JSON objects into a JSON array of all the types of the structure diff.
 * 
 */
public class OracleGeneratorClient {

    private static final Logger log = LoggerFactory.getLogger(OracleGeneratorClient.class);

    /**
     * This method generates the structure oracle by scanning the Java projects contained in the paths passed as arguments.
     * @param solutionProjectPath: The path to the project of the solution of a programming exercise.
     * @param templateProjectPath: The path to the project of the template of a programming exercise.
     * @return The string of the JSON representation of the structure oracle.
     */
    public static String generateStructureOracleJSON(Path solutionProjectPath, Path templateProjectPath) {
        log.debug("GENERATING THE ORACLE FOR THE FOLLOWING PROJECTS: \n"
            + "Solution project: " + solutionProjectPath + "\n"
            + "Template project: " + templateProjectPath + "\n");

        // Initialize the empty string.
        JSONArray structureOracleJSON = new JSONArray();

        // Generate the pairs of the types found in the solution project with the corresponding one from the template project.
        Map<CtType<?>, CtType<?>> solutionAndTemplateTypes = generateSolutionAndTemplateTypePairs(solutionProjectPath, templateProjectPath);

        // Loop over each pair of types and create the diff data structures and the JSON representation afterwards for each.
        // If the types, classes or enums are equal, then ignore and continue with the next pair
        for (Map.Entry<CtType<?>, CtType<?>> entry : solutionAndTemplateTypes.entrySet()) {
            JSONObject diffJSON = new JSONObject();

            CtType<?> solutionType = entry.getKey();
            CtType<?> templateType = entry.getValue();

            // Initialize the types diff containing various properties as well as methods.
            TypesDiff typesDiff = new TypesDiff(solutionType, templateType);
            if(typesDiff.typesEqual) {
                continue;
            }

            // If we are dealing with interfaces, the types diff already has all the information we need
            // So we do not need to do anything more
            TypesDiffSerializer typesDiffSerializer = new TypesDiffSerializer(typesDiff);
            diffJSON.put("class", typesDiffSerializer.serializeHierarchy());
            if(!typesDiff.methodsDiff.isEmpty()) {
                diffJSON.put("methods", typesDiffSerializer.serializeMethods());
            }

            // Otherwise check then if the current types are enums or classes and create the corresponding
            // diffs in order to extract specific elements.

            if (solutionType.isEnum()) {
                CtEnum<Enum<?>> solutionEnum = (CtEnum<Enum<?>>) solutionType;
                CtEnum<Enum<?>> templateEnum = (CtEnum<Enum<?>>) templateType;

                EnumsDiff enumsDiff = new EnumsDiff(solutionEnum, templateEnum);
                if(enumsDiff.enumsEqual) {
                    continue;
                }

                EnumsDiffSerializer enumsDiffSerializer = new EnumsDiffSerializer(enumsDiff);
                if(!enumsDiff.enumValuesDiff.isEmpty()) {
                    diffJSON.put("enumValues", enumsDiffSerializer.serializeEnumValues(enumsDiff));
                }
            }

            if (solutionType.isClass()) {
                CtClass<?> solutionClass = (CtClass<?>) solutionType;
                CtClass<?> templateClass = (CtClass<?>) templateType;

                ClassesDiff classesDiff = new ClassesDiff(solutionClass, templateClass);
                if(classesDiff.classesEqual) {
                    continue;
                }

                ClassesDiffSerializer classesDiffSerializer = new ClassesDiffSerializer(classesDiff);
                if(!classesDiff.attributesDiff.isEmpty()) {
                    diffJSON.put("attributes", classesDiffSerializer.serializeAttributes());
                }
                if(!classesDiff.constructorsDiff.isEmpty()) {
                    diffJSON.put("constructors", classesDiffSerializer.serializeConstructors());
                }
            }

            log.debug("Generated JSON for '" + solutionType.getSimpleName() + "'.");
            structureOracleJSON.put(diffJSON);
        }

        return prettyPrint(structureOracleJSON);
    }

    /**
     * This method pretty prints a given JSON array.
     * @param jsonArray The JSON array that needs to get pretty printed.
     * @return The pretty printed JSON array in its string representation. If there is any IO exception, an empty string is returned instead.
     */
    private static String prettyPrint(JSONArray jsonArray) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Object jsonObject = mapper.readValue(jsonArray.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (IOException e) {
            log.error("Could not pretty print the JSON!");
            return "";
        }
    }

    /**
     * This method scans the meta models of the solution and template projects and generates pairs of matching types.
     * Matching types means here types with the same name, since they are uniquely defined from their names.
     * The list of the type pairs contains with certainty all the types found in the solution project, but it often
     * happens that no corresponding types are declared in the template.
     * For this, a null type is inserted instead and handled accordingly in the diffs.
     * Also, if the type in the template is the same to the one in the solution, then they get ignored and do not
     * get added to the structure oracle.
     * @param solutionProjectPath: The path to the solution project.
     * @param templateProjectPath: The path to the template project.
     * @return: A hash map containing the type pairs of the solution types and their respective counterparts in the template.
     */
    private static Map<CtType<?>, CtType<?>> generateSolutionAndTemplateTypePairs(Path solutionProjectPath, Path templateProjectPath) {
        Collection<CtType<?>> solutionTypes = generateModel(solutionProjectPath).getAllTypes();
        Collection<CtType<?>> templateTypes = generateModel(templateProjectPath).getAllTypes();

        Map<CtType<?>, CtType<?>> solutionAndTemplateTypes = new HashMap<CtType<?>, CtType<?>>();

        for(CtType<?> solutionType : solutionTypes) {
            // Put an empty template class as a default placeholder.
            solutionAndTemplateTypes.put(solutionType, null);

            for(CtType<?> templateType : templateTypes) {
                // If an exact same template class is found, then remove the pair and continue
                if(solutionType.equals(templateType)) {
                    solutionAndTemplateTypes.remove(solutionType);
                    break;
                } else if(solutionType.getSimpleName().equals(templateType.getSimpleName())) {
                    // If a template class with the same name gets found, then replace the empty template with the real one.
                    solutionAndTemplateTypes.put(solutionType, templateType);
                    break;
                }
            }
        }

        return solutionAndTemplateTypes;
    }

    /**
     * This method extracts the meta model of a Java project using the Spoon Framework in order to query the types
     * contained in it.
     * @param projectPath: The path of the Java project the meta model is needed for.
     * @return: The meta model of the project.
     */
    private static CtModel generateModel(Path projectPath) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(projectPath.toString());
        launcher.buildModel();
        return launcher.getModel();
    }

}
