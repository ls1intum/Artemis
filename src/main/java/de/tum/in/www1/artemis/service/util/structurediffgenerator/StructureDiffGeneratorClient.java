package de.tum.in.www1.artemis.service.util.structurediffgenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONArray;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Kristian Dimo (kristian.dimo@tum.de)
 * 
 * This parser is used to parse the structure of a given programming exercise 
 * to be then assessed from ArTEMiS. It is used to automatically generate the test.json 
 * file that is used for the execution of the structural tests for these exercises.
 * 
 * The parser uses the Spoon framework (http://spoon.gforge.inria.fr/) from the INRIA
 * institute, the makers of OCaml. This framework is able to create the abstract
 * syntax tree by parsing code as a string.
 * 
 * The extracted code structure elements are then manually assembled into
 * the test.json file. The assembling is done manually, because we are interested
 * only in some elements, like the classes' names and fields and
 * the fields' and methods' names, modifiers and parameters (where applicable).
 * 
 * But the test.json file should only contain the structural elements that need
 * to be implemented from the student. This means that the resulting JSON object 
 * contains the elements of the solution code that the template code (the code
 * that the students start with) does not contain.
 * 
 * The steps this custom parser takes are the following:
 * 	1. Feed the parser the path to the Maven projects of the solution and 
 *	 	template projects and also the path to the test.json file.
 * 	2. Extract all the types (classes, enums, interfaces, annotations)
 * 		from the projects using the Spoon Framework.
 * 	3. Create pairs of the solution types and their corresponding template
 * 		counterparts.
 * 	4. Extract the needed code structure elements.
 *  5. Check here which elements are present in the solution code and not in the 
 *  	template code (the diff).
 * 	6. Assemble the JSON arrays/objects for the diff of each structural element
 * 		for each class.
 * 	7. Assemble the whole JSON array for all the class files in the project that
 * 		are needed for the test.json file.
 * 
 */

public class StructureDiffGeneratorClient {
		
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
			System.out.println("\nSuccessfully wrote the JSON file!");
		} catch (IOException e) {
			System.out.println("Couldn't write the JSON file! Check the save file path: " + saveFilePath);
		}
	}

	public static void run(String solutionProjectRootPath, String templateProjectRootPath, String structureDiffFilePath, String structureDiffFileName) {
	/** Pass as program arguments:
	 * 	1. The path to the solution project root folder,
	 * 	2. The path to the template project root folder ,
	 * 	3. The desired file path of the structure diff file.
	 *  4. The desired file name of the structure diff file. */
		System.out.println("GENERATING THE STRUCTURE DIFF JSON FOR THE FOLLOWING PROJECTS: \n"
				+ "Solution project: " + solutionProjectRootPath + "\n"
				+ "Template project: " + templateProjectRootPath + "\n");
		
		JSONArray structureDiffJSON = StructureDiffJSONFactory.generateStructureDiffJSON(solutionProjectRootPath, templateProjectRootPath);
		
		saveFile(structureDiffJSON, structureDiffFilePath + structureDiffFileName);
	}

}
