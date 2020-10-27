package ${packageName}.testutils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import me.xdrop.fuzzywuzzy.FuzzySearch;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 3.0 (25.09.2019)
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
 * - Undefined, which is used to initialize the scan result.
 *
 *  A note on the limit of allowed number of typos: the maximal number depends
 *  on the length of the class name and is defined as ceiling(classNameLength / 4).
 */
public class ClassNameScanner {

    // The class name and package name of the expected class that is currently being searched after.
    private final String expectedClassName;
    private final String expectedPackageName;

    // The names of the classes observed in the project
    private final Map<String, List<String>> observedClasses;
    private final ScanResult scanResult;

    public ClassNameScanner(String expectedClassName, String expectedPackageName) {
        this.expectedClassName = expectedClassName;
        this.expectedPackageName = expectedPackageName;
        this.observedClasses = retrieveObservedClasses();
        this.scanResult = computeScanResult();
    }

    public ScanResult getScanResult() {
        return this.scanResult;
    }

    /**
     * This method computes the scan result of the submission for the expected class name.
     * It first checks if the class is in the project at all.
     * If that's the case, it then checks if that class is properly placed or not and generates feedback accordingly.
     * Otherwise the method loops over the observed classes and checks if any of the observed classes is actually the
     * expected one but with the wrong case or types in the name.
     * It again checks in each case if the class is misplaced or not and delivers the feedback.
     * Finally, in none of these holds, the class is simply declared as not found.
     *
     * @return An instance of ScanResult containing the result type and the feedback message.
     */
    private ScanResult computeScanResult() {
        // Initialize the type and the message of the scan result.
        ScanResultType scanResultType = ScanResultType.UNDEFINED;
        String scanResultMessage = "";

        boolean classIsFound = observedClasses.containsKey(expectedClassName);
        boolean classIsCorrectlyPlaced;
        boolean classIsPresentMultipleTimes;

        String foundObservedClassName = null;
        String foundObservedPackageName = null;

        if(classIsFound) {
            List<String> observedPackageNames = observedClasses.get(expectedClassName);
            classIsPresentMultipleTimes = observedPackageNames.size() > 1;
            classIsCorrectlyPlaced = classIsPresentMultipleTimes ? false : (observedPackageNames.contains(expectedPackageName));

            scanResultType = classIsPresentMultipleTimes ? ScanResultType.CORRECTNAME_MULTIPLETIMESPRESENT :
                (classIsCorrectlyPlaced ? ScanResultType.CORRECTNAME_CORRECTPLACE : ScanResultType.CORRECTNAME_MISPLACED);

            foundObservedClassName = expectedClassName;
            foundObservedPackageName = observedPackageNames.toString();
        }
        else {
            for(String observedClassName : observedClasses.keySet()) {
                Collection<String> observedPackageNames = observedClasses.get(observedClassName);
                classIsPresentMultipleTimes = observedPackageNames.size() > 1;
                classIsCorrectlyPlaced = classIsPresentMultipleTimes ? false : (observedPackageNames.contains(expectedPackageName));

                boolean hasWrongCase = observedClassName.equalsIgnoreCase(expectedClassName);
                boolean hasTypos = FuzzySearch.ratio(observedClassName, expectedClassName) > 90;
                //The previous implementation
//              boolean hasTypos = DiffUtils.levEditDistance(observedClassName, expectedClassName, 1) < Math.ceil(expectedClassName.length() / 4);

                foundObservedClassName = observedClassName;
                foundObservedPackageName = observedPackageNames.toString();

                if(hasWrongCase) {
                    scanResultType = classIsPresentMultipleTimes ? ScanResultType.WRONGCASE_MULTIPLETIMESPRESENT :
                        (classIsCorrectlyPlaced ? ScanResultType.WRONGCASE_CORRECTPLACE : ScanResultType.WRONGCASE_MISPLACED);
                    break;
                } else if(hasTypos) {
                    scanResultType = classIsPresentMultipleTimes ? ScanResultType.TYPOS_MULTIPLETIMESPRESENT :
                        (classIsCorrectlyPlaced ? ScanResultType.TYPOS_CORRECTPLACE : ScanResultType.TYPOS_MISPLACED);
                    break;
                } else {
                    scanResultType = ScanResultType.NOTFOUND;
                }
            }
        }

        switch (scanResultType) {
            case CORRECTNAME_CORRECTPLACE :
                scanResultMessage = "The class " + foundObservedClassName + " has the correct name and is in the correct package.";
                break;
            case CORRECTNAME_MISPLACED :
                scanResultMessage = "The class " + foundObservedClassName + " has the correct name,"
                    + " but the package it's in, " + foundObservedPackageName + ", deviates from the expectation."
                    + "  Make sure it is placed in the correct package.";
                break;
            case CORRECTNAME_MULTIPLETIMESPRESENT:
                scanResultMessage = "The class " + foundObservedClassName + " has the correct name,"
                    + " but it is located multiple times in the project and in the packages: "
                    + foundObservedPackageName +", which deviates from the expectation."
                    + " Make sure to place the class in the correct package and remove any superfluous ones.";
                break;
            case WRONGCASE_CORRECTPLACE:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName
                    + ". We found that you implemented a class " + foundObservedClassName + ", which deviates from the expectation."
                    + " Check for wrong upper case / lower case lettering.";
                break;
            case WRONGCASE_MISPLACED:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName + " in the package " + expectedPackageName
                    + ". We found that you implemented a class " + foundObservedClassName + ", in the package " + foundObservedPackageName
                    + ", which deviates from the expectation."
                    + " Check for wrong upper case / lower case lettering and make sure you place it in the correct package.";
                break;
            case WRONGCASE_MULTIPLETIMESPRESENT:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName + " in the package " + expectedPackageName
                    + ". We found that you implemented a class " + foundObservedClassName + ", in the packages " + foundObservedPackageName
                    + ", which deviates from the expectation."
                    + " Check for wrong upper case / lower case lettering and make sure you place one class in the correct package and remove any superfluous classes.";
                break;
            case TYPOS_CORRECTPLACE:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName
                    + ". We found that you implemented a class " + foundObservedClassName + ", which deviates from the expectation."
                    + " Check for typos in the class name.";
                break;
            case TYPOS_MISPLACED:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName + " in the package " + expectedPackageName
                    + ". We found that you implemented a class " + foundObservedClassName + ", in the package " + foundObservedPackageName
                    + ", which deviates from the expectation."
                    + " Check for typos in the class name and make sure you place it in the correct package.";
                break;
            case TYPOS_MULTIPLETIMESPRESENT:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName + " in the package " + expectedPackageName
                    + ". We found that you implemented a class " + foundObservedClassName + ", in the packages " + observedClasses.get(foundObservedClassName).toString()
                    + ", which deviates from the expectation."
                    + " Check for typos in the class name and make sure you place one class it in the correct package and remove any superfluous classes.";
                break;
            case NOTFOUND:
                scanResultMessage = "The exercise expects a class with the name " + expectedClassName + " in the package " + expectedPackageName
                    + ". You did not implement the class in the exercise.";
                break;
            default:
                scanResultMessage = "The class could not be scanned.";
                break;
        }

        return new ScanResult(scanResultType, scanResultMessage);
    }

    /**
     * This method retrieves the actual type names and their packages by walking the project file structure.
     * The root node (which is the assignment folder) is defined in the pom.xml file of the project.
     * @return The map containing the type names as keys and the type packages as values.
     */
    private Map<String, List<String>> retrieveObservedClasses() {
        Map<String, List<String>> observedTypes = new HashMap<>();

        try {
            File pomFile = new File("pom.xml");
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document pomXmlDocument = documentBuilder.parse(pomFile);

            NodeList buildNodes = pomXmlDocument.getElementsByTagName("build");
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
            System.err.println("Could not retrieve the source directory from the pom.xml file. Contact your instructor.");
            e.printStackTrace();
        }

        return observedTypes;
    }

    /**
     * This method recursively walks the actual folder file structure starting from the assignment folder and adds
     * each type it finds e.g. filenames ending in .java to the passed JSON object.
     * @param assignmentFolderName: The root folder where the method starts walking the project structure.
     * @param node: The current node the method is visiting.
     * @param foundTypes: The JSON object where the type names and packages get appended.
     */
    private void walkProjectFileStructure(String assignmentFolderName, File node, Map<String, List<String>> foundTypes) {
        String fileName = node.getName();

        if(fileName.contains(".java") || fileName.contains(".kt")) {
            String[] fileNameComponents = fileName.split("\\.");

            String className = fileNameComponents[fileNameComponents.length - 2];
            int until = node.getPath().indexOf(".java");
            if (until == -1) {
                until = node.getPath().indexOf(".kt");
            }
            String packageName = node.getPath().substring(0, until);
            packageName = packageName.substring(
                packageName.indexOf(assignmentFolderName) + assignmentFolderName.length() + 1,
                packageName.lastIndexOf(File.separator + className));
            packageName = packageName.replace(File.separatorChar, '.');

            if(foundTypes.containsKey(className)) {
                foundTypes.get(className).add(packageName);
            }
            else {
                foundTypes.put(className, Arrays.asList(packageName));
            }
        }

        if(node.isDirectory()) {
            String[] subNodes = node.list();
            for(String currentSubNode : subNodes) {
                walkProjectFileStructure(assignmentFolderName, new File(node, currentSubNode), foundTypes);
            }
        }
    }
}
