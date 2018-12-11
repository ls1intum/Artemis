package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author Kristian Dimo (kristian.dimo@tum.de)
 * 
 * This generator is used to parse the structure of a given programming exercise
 * to be then assessed from ArTEMiS. It is used to automatically generate the test.json 
 * file that is used for the execution of the structural tests for these exercises.
 * 
 * The generator uses the Spoon framework (http://spoon.gforge.inria.fr/) from the INRIA
 * institute.
 * 
 * The extracted code structure elements are then manually assembled into
 * a JSON file called the structure diff fiel. The assembling is done manually, because
 * we are interested only in some elements, like the classes' names and fields and
 * the fields' and methods' names, modifiers and parameters (where applicable).
 * 
 * But the structure diff file should only contain the structural elements that need
 * to be implemented from the student. This means that the resulting JSON
 * contains the elements of the solution code that the template code (the code
 * that the students start with) does not contain.
 * 
 * The steps the structure diff generator takes are the following:
 * 	1. Feed the Spoon Framework the path to the projects of the solution and
 *	 	template projects and also the path where the structure diff file needs to be saved.
 * 	2. Extract all the types (classes, enums, interfaces, annotations)
 * 		from the projects' model using the Spoon Framework.
 * 	3. Create pairs of the solution types and their corresponding template
 * 		counterparts.
 * 	4. Extract the needed code structure elements.
 *  5. Check here which elements are present in the solution code and not in the 
 *  	template code (the diff).
 * 	6. Assemble the JSON objects for the diff of each structural element
 * 		for each class.
 * 	7. Assemble the JSON objects into a JSONArray of all the types of the structure diff.
 * 
 */

public class StructureDiffGeneratorClient {

    private static final Logger log = LoggerFactory.getLogger(StructureDiffGeneratorClient.class);

    private static String prettyPrint(JSONArray jsonArray) {
		String prettyJSON = "";
		
		ObjectMapper mapper = new ObjectMapper();
        try {
        	Object jsonObject = mapper.readValue(jsonArray.toString(), Object.class);
            prettyJSON = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (IOException e) {
        	System.out.println("Could not pretty print the JSON!");
        }
                
        return prettyJSON;
	}
	
	/** This method writes the passed JSON array into a file with the given file name. */
	private static void saveFile(JSONArray jsonArray, String saveFilePath) {
		System.out.println("\nSaving JSON file in: " + saveFilePath + " ...");
		
		String prettyJSON =  prettyPrint(jsonArray);
        
		try {
			Files.write(Paths.get(saveFilePath), prettyJSON.getBytes());
			log.info("\nSuccessfully wrote the JSON file in: " + saveFilePath + "!");
		} catch (IOException e) {
			log.error("Couldn't write the JSON file! Check the save file path: " + saveFilePath, e);
		}
	}

	public static void run(String solutionProjectRootPath, String templateProjectRootPath, String structureDiffFilePath, String structureDiffFileName) {
	/** Pass as program arguments:
	 * 	1. The path to the solution project root folder,
	 * 	2. The path to the template project root folder ,
	 * 	3. The desired file path of the structure diff file.
	 *  4. The desired file name of the structure diff file. */
		log.info("GENERATING THE STRUCTURE DIFF JSON FOR THE FOLLOWING PROJECTS: \n"
				+ "Solution project: " + solutionProjectRootPath + "\n"
				+ "Template project: " + templateProjectRootPath + "\n");
		
		JSONArray structureDiffJSON = StructureDiffJSONFactory.generateStructureDiffJSON(solutionProjectRootPath, templateProjectRootPath);
		
		saveFile(structureDiffJSON, structureDiffFilePath + structureDiffFileName);
	}

}
