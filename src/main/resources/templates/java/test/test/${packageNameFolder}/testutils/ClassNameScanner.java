package ${packageName}.testutils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Kristian Dimo (kristian.dimo@tum.de)
 * @version 1.1 (28.01.2018)
 * 
 * This class scans the submission project if the current expected class is actually 
 * present in it or not. The result is returned as an instance of ScanResult.
 * The ScanResult consists of a ScanResultType and a ScanResultMessage as a string.
 * ScanResultType is an enum and is implemented so that identifying just the type of 
 * the error and the binding of several messages to a certain result is possible. 
 * 
 * There are the following possible results:
 * - The class has the correct name and is placed in the correct package.
 * - The class has the correct name but is misplaced.
 * - The class name has wrong casing, but is placed in the correct package.
 * - The class name has wrong casing and is misplaced.
 * - The class name has typos, but is placed in the correct package.
 * - The class name has typos and is misplaced.
 * - The class name has too many typos, thus is declared as not found.
 * - Undefined, which is used to initialise the scan result.
 *  
 *  A note on the limit of allowed number of typos: the maximal number depends
 *  on the length of the class name and is defined as ceiling(classNameLength / 4).
 */
public class ClassNameScanner {
	
	// The class name and package name of the expected class that is currently being searched after.
	private final String expectedClassName;
	private final String expectedPackageName;
	
	// The names of the classes observed in the project
	private final HashMap<String, String> observedClasses;
	private final HashMap<String, String> expectedClasses;
	private final ScanResult scanResult;
	
	public ClassNameScanner(String expectedClassName, String expectedPackageName, JSONArray structureOracleJSON) {
		this.expectedClassName = expectedClassName;
		this.expectedPackageName = expectedPackageName;	
		this.observedClasses = retrieveObservedClasses();
		this.expectedClasses = retrieveExpectedClasses(structureOracleJSON);
		this.scanResult = computeScanResult();
	}
	
	public ScanResult getScanResult() { return this.scanResult; }
	
	/**
	 * This method computes the scan result of the submission for the expected class name.
	 * It first checks if the class is in the project at all.
	 * If that's the case, it then checks if that class is properly placed or not and generates feedback accordingly.
	 * Otherwise the method loops over the observed classes and checks if any of the observed classes is actually the
	 * expected one but with the wrong case or types in the name.
	 * It again checks in each case if the class is misplaced or not and delivers the feedback.
	 * Finally, in none of these holds, the class is simply declared as not found.
	 * @return An instance of ScanResult containing the result type and the feedback message.
	 */
	private ScanResult computeScanResult() {
		// Initialise the type and the message of the scan result.
		ScanResultType scanResultType = ScanResultType.UNDEFINED;
		String scanResultMessage = "";
		
		boolean classIsFound = observedClasses.containsKey(expectedClassName);
		boolean classIsCorrectlyPlaced;
		
		if(classIsFound) {
			String observedPackageName = observedClasses.get(expectedClassName);
			classIsCorrectlyPlaced = observedPackageName.equals(expectedPackageName);
			
			scanResultType = classIsCorrectlyPlaced 
					? ScanResultType.CORRECTNAME_CORRECTPLACE 
					: ScanResultType.CORRECTNAME_MISPLACED;
			scanResultMessage = classIsCorrectlyPlaced
					? "The class " + expectedClassName + " has the correct name and is in the correct package." 
					: "The class " + expectedClassName + " has the correct name,"
							+ " but the package it's in, " + observedClasses.get(expectedClassName) + ", deviates from the expectation."
							+ "  Please make sure it is placed in the correct package.";
		} else {			
			for(String observedClassName : observedClasses.keySet()) {
				String observedPackageName = observedClasses.get(observedClassName);
				classIsCorrectlyPlaced = observedPackageName.equals(expectedPackageName);

				boolean hasWrongCase = observedClassName.equalsIgnoreCase(expectedClassName);	
				boolean hasTypos = new LevenshteinDistance().apply(observedClassName, expectedClassName) < Math.ceil(expectedClassName.length() / 4);

				if(hasWrongCase) {
					scanResultType = classIsCorrectlyPlaced 
							? ScanResultType.WRONGCASE_CORRECTPLACE
							: ScanResultType.WRONGCASE_MISPLACED;
					scanResultMessage = classIsCorrectlyPlaced
							? "The exercise expects a class with the name " + expectedClassName
									+ ". We found that you implemented a class " + observedClassName + ", which deviates from the expectation."
									+ " Please check for wrong upper case / lower case lettering."
							: "The exercise expects a class with the name " + expectedClassName + " in the package " + expectedPackageName
									+ ". We found that you implemented a class " + observedClassName + ", in the package " + observedClassName
									+ ", which deviates from the expectation."
									+ " Please check for wrong upper case / lower case lettering and make sure you place it in the correct package.";
					break;
				} else if(hasTypos) {
					scanResultType = classIsCorrectlyPlaced 
							? ScanResultType.TYPOS_CORRECTPLACE
							: ScanResultType.TYPOS_MISPLACED;
					scanResultMessage = classIsCorrectlyPlaced
							? "The exercise expects a class with the name " + expectedClassName
									+ ". We found that you implemented a class " + observedClassName + ", which deviates from the expectation."
									+ " Please check for typos in the class name."
							: "The exercise expects a class with the name " + expectedClassName + "in the package " + expectedPackageName
									+ ". We found that you implemented a class " + observedClassName + ", in the package " + observedPackageName
									+ ", which deviates from the expectation."
									+ " Please check for typos in the class name and make sure you place it in the correct package.";
					break;
				} else {
					scanResultType = ScanResultType.NOTFOUND;
					scanResultMessage = "You have implemented " + observedClassName + " in the package " + observedClassName
							+ ". This class is not expected in the exercise."
							+ "\n The assignment expects the following classes and in the according packages: \n";
					
					// Append the expected classes and package names to the result message
					for(String expectedClassName : expectedClasses.keySet()) {
						scanResultMessage += "Class: " + expectedClassName +
											" Package:" + expectedClasses.get(expectedClassName) +",\n";
					} 
				}
			}
		}
		
		return new ScanResult(scanResultType, scanResultMessage);
	}
	
	/**
	 * This method retrieves the actual type names and their packages by walking the project file structure.
	 * The root node (which is the assignment folder) is defined in the pom.xml file of the project.
	 * @return The JSON object containing the type names as keys and the type packages as values.
	 */
	private Map<String, String> retrieveObservedClasses() {
		Map<String, String> observedTypes = new HashMap<String, String>();
		
		try {
			File pomFile = new File("pom.xml");
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			Document pomDocument = documentBuilder.parse(pomFile);
						
			NodeList buildNodes = pomDocument.getElementsByTagName("build");
			for(int i = 0; i < buildNodes.getLength(); i++) {
				Node buildNode = buildNodes.item(i);
				
				if(buildNode.getNodeType() == Node.ELEMENT_NODE) {
					Element buildNodeElement = (Element) buildNode;
					String sourceDirectoryPropertyValue = buildNodeElement.getElementsByTagName("sourceDirectory").item(0).getTextContent();
					String assignmentFolderName = sourceDirectoryPropertyValue.substring(sourceDirectoryPropertyValue.indexOf("}") + 2);
					walkProjectFileStructure(assignmentFolderName, new File(assignmentFolderName), observedTypes);
				}
			}
		} catch (ParserConfigurationException | SAXException | IOException e) {
			System.err.println("Could not retrieve the source directory from the pom.xml file. Please contact your instructor immediately.");
			e.printStackTrace();
		}
				
		return observedTypes;
	}
	
	/**
	 * This method recursively walks the actual folder file structure starting from the assignment folder and adds
	 * each type it finds e.g. filenames ending in .java to the passed JSON object.
	 * @param assignmentFolderName: The root folder where the method starts walking the project structure.
	 * @param node: The current node the method is visiting.
	 * @param types: The JSON object where the type names and packages get appended.
	 */
	private void walkProjectFileStructure(String assignmentFolderName, File node, HashMap<String, String> types) {
		String currentFileName = node.getName();

		if(currentFileName.contains(".java")) {	
			String[] currentFileNameComponents = currentFileName.split("\\.");
			
			String className = currentFileNameComponents[currentFileNameComponents.length - 2];
			String packageName = node.getPath().substring(0, node.getPath().indexOf(".java"));
			packageName = packageName.substring(
					packageName.indexOf(assignmentFolderName) + assignmentFolderName.length() + 1, 
					packageName.lastIndexOf(File.separator + className));
			packageName = packageName.replace(File.separatorChar, '.');
			
			types.put(className, packageName);
		}

		if(node.isDirectory()) {
			String[] subNodes = node.list();		
			for(String currentSubNode : subNodes) {	
				walkProjectFileStructure(assignmentFolderName, new File(node, currentSubNode), types);
			}
		}
	}
	
	/**
	 * This method retrieves the expected type names and their packages from the JSON representation of the structure diff.
	 * @param structureDiffJSON: The JSON representation of the structure diff.
	 * @return The JSON object containing the type names as keys and the type packages as values.
	 */
	private Map<String, String> retrieveExpectedClasses(JSONArray structureDiffJSON) {
		Map<String, String> expectedTypes = new HashMap<String, String>();
		
		for (int i = 0; i < structureDiffJSON.length(); i++) {
			JSONObject currentTypeJSON = (JSONObject) structureDiffJSON.get(i);
			JSONObject currentTypePropertiesJSON = currentTypeJSON.getJSONObject("class");
				
			String expectedClassName = currentTypePropertiesJSON.getString("name");
			String expectedPackageName = currentTypePropertiesJSON.getString("package");
			
			expectedTypes.put(expectedClassName, expectedPackageName);
		}
		
		return expectedTypes;
	}

}
