package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
     * This method serves as a facade to the Oracle Generator. It generates the oracle for the projects in the given
     * paths and saves the file in the given file path with the given file name.
     * @param solutionProjectRootPath The path to the root of the project containing the solution of a programming exercise.
     * @param templateProjectRootPath The path to the root of the project containing the template of a programming exercise.
     * @param oracleFilePath The path to the directory where the oracle file should be saved in.
     * @param oracleFileName The file name of the oracle file.
     */
	public static void run(String solutionProjectRootPath, String templateProjectRootPath, String oracleFilePath, String oracleFileName) {
		log.info("GENERATING THE ORACLE FOR THE FOLLOWING PROJECTS: \n"
				+ "Solution project: " + solutionProjectRootPath + "\n"
				+ "Template project: " + templateProjectRootPath + "\n");
		
		String oracleJSON = OracleJSONFactory.generateStructureOracleJSON(solutionProjectRootPath, templateProjectRootPath, log);
		
		saveFile(oracleJSON, oracleFilePath, oracleFileName);
	}

    /**
     * This method writes the contents of the passed string into the given file path.
     * @param string The string that needs to get written.
     * @param filePath The path of the directory where the JSON array will get written in.
     * @param fileName The name of the file that will get written.
     */
    private static void saveFile(String string, String filePath, String fileName) {
        log.info("\nSaving " + fileName + " in: " + filePath + " ...");

        try {
            String fullFilePath = filePath + File.separator + fileName;
            Files.write(Paths.get(fullFilePath), string.getBytes());
            log.info("\nSuccessfully wrote " + fileName + " in: " + filePath + "!");
        } catch (IOException e) {
            log.error("Couldn't write " + fileName + "! Check the file path: " + filePath, e);
        }
    }

    /**
     * This method return the pretty printed string of a given JSON array.
     * @param jsonArray The JSON array that needs to get pretty printed.
     * @return The pretty printed string representation of the JSON array.
     */
    private static String prettyPrint(JSONArray jsonArray) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object jsonObject = mapper.readValue(jsonArray.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (IOException e) {
            log.error("Could not pretty print the JSON!", e);
            return null;
        }
    }

}
