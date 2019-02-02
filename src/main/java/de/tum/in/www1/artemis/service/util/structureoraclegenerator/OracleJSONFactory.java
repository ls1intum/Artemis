package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;

import spoon.Launcher;

import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;

import org.slf4j.Logger;

/**
 * This class serves as a factory for the oracle.
 * Its main function is to generate the string of the JSON representation of the structure oracle.
 */
public class OracleJSONFactory {

    /**
     * This method generates the structure oracle by scanning the Java projects contained in the paths passed as arguments.
     * @param solutionProjectPath: The path to the project of the solution of a programming exercise.
     * @param templateProjectPath: The path to the project of the template of a programming exercise.
     * @param log: The logger used to display information about critical points of this process in the console.
     * @return The string of the JSON representation of the structure oracle.
     */
    public static String generateStructureOracleJSON(String solutionProjectPath, String templateProjectPath, Logger log) {

        // Initialize the empty string.
        String structureOracleJSON = "";

        // Generate the pairs of the types found in the solution project with the corresponding one from the template project.
        HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = generateSolutionAndTemplateTypePairs(solutionProjectPath, templateProjectPath);

        // Loop over each pair of types and create the diff data structures and the JSON representation afterwards for each.
        for (Map.Entry<CtType<?>, CtType<?>> entry : solutionAndTemplateTypes.entrySet()) {
            CtType<?> solutionType = entry.getKey();
            CtType<?> templateType = entry.getValue();

            // Initialize the types diff containing various properties as well as methods.
            TypesDiff typesDiff = new TypesDiff(solutionType, templateType);
            if(typesDiff.typesEqual) { continue; }
            String typesDiffJSON = generateTypesDiffJSON(typesDiff, log);

            // Check then if the current types are enums, interfaces or classes and create the corresponding
            // diffs in order to extract specific elements.

            if (solutionType.isEnum()) {
                CtEnum<Enum<?>> solutionEnum = (CtEnum<Enum<?>>) solutionType;
                CtEnum<Enum<?>> templateEnum = (CtEnum<Enum<?>>) templateType;

                EnumsDiff enumsDiff = new EnumsDiff(solutionEnum, templateEnum);
                if(enumsDiff.enumsEqual) { continue; }

                typesDiffJSON += generateTypesDiffJSON(enumsDiff, log);
            }

            if (solutionType.isInterface()) {
                CtInterface<?> solutionInterface = (CtInterface<?>) solutionType;
                CtInterface<?> templateInterface = (CtInterface<?>) templateType;

                InterfacesDiff interfacesDiff = new InterfacesDiff(solutionInterface, templateInterface);
                if(interfacesDiff.interfacesEqual) { continue; }

                typesDiffJSON += generateTypesDiffJSON(interfacesDiff, log);
            }

            if (solutionType.isClass()) {
                CtClass<?> solutionClass = (CtClass<?>) solutionType;
                CtClass<?> templateClass = (CtClass<?>) templateType;

                ClassesDiff classesDiff = new ClassesDiff(solutionClass, templateClass);
                if(classesDiff.classesEqual) { continue; }

                typesDiffJSON += generateTypesDiffJSON(classesDiff, log);
            }

            structureOracleJSON += typesDiffJSON;
        }

        return structureOracleJSON;
    }

    /**
     * This method generates the string representation of a single diff by serializing the diff.
     * @param typesDiff: The diff we want to generate the JSON string for.
     * @param log: The logger used to display information about critical points of this process in the console.
     * @return: The JSON string of the diff.
     */
    private static String generateTypesDiffJSON(TypesDiff typesDiff, Logger log) {
        try {
            log.info("Creating JSON for: " + typesDiff.name + "'.");
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setVisibility(JsonMethod.FIELD, JsonAutoDetect.Visibility.ANY);
            String typesDiffJSON = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(typesDiff);
            log.info("Successfully created the JSON for: '" + typesDiff.name + "'.");
            return typesDiffJSON;
        } catch (IOException e) {
            log.error("Could not create the JSON for: " + typesDiff.name + "'.", e);
            return "";
        }
    }

    /**
     * This method scans the metamodels of the solution and template projects and generates pairs of matching types.
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
	private static HashMap<CtType<?>, CtType<?>> generateSolutionAndTemplateTypePairs(String solutionProjectPath, String templateProjectPath) {
		Collection<CtType<?>> solutionTypes = generateModel(solutionProjectPath).getAllTypes();
		Collection<CtType<?>> templateTypes = generateModel(templateProjectPath).getAllTypes();
		
		HashMap<CtType<?>, CtType<?>> solutionAndTemplateTypes = new HashMap<CtType<?>, CtType<?>>();

		for(CtType<?> solutionType : solutionTypes) {
		    // Put an empty template class as a default placeholder.
            solutionAndTemplateTypes.put(solutionType, null);

		    for(CtType<?> templateType : templateTypes) {
		        // If an exact same template class is found, then remove the pair and continue
		        if(solutionType.equals(templateType)) {
		            solutionAndTemplateTypes.remove(solutionType);
		            continue;
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
     * This method extracts the metamodel of a Java project using the Spoon Framework in order to query the types
     * contained in it.
     * @param projectPath: The path of the Java project the metamodel is needed for.
     * @return: The metamodel of the project.
     */
	private static CtModel generateModel(String projectPath) {
		Launcher launcher = new Launcher();
		launcher.addInputResource(projectPath); 
		launcher.buildModel();
		return launcher.getModel();
	}
	
}
