package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class JenkinsBuildPlanUtils {

    private static final String PIPELINE_SCRIPT_DETECTION_COMMENT = "// ARTEMIS: JenkinsPipeline";

    /**
     * Replaces the base repository url written within the Jenkins pipeline script with the value specified by repoUrl
     *
     * @param jobXmlDocument the Jenkins pipeline
     * @param repoUrl the new repository url
     * @param baseRepoUrl the base repository url that will be replaced
     * @throws IllegalArgumentException if the xml document isn't a Jenkins pipeline script
     */
    public static void replaceScriptParameters(Document jobXmlDocument, String repoUrl, String baseRepoUrl) throws IllegalArgumentException {
        final var scriptNode = findScriptNode(jobXmlDocument);
        if (scriptNode == null || scriptNode.getFirstChild() == null) {
            throw new IllegalArgumentException("Pipeline Script not found");
        }

        String pipeLineScript = scriptNode.getFirstChild().getTextContent().trim();
        // If the script does not start with "pipeline" or the special comment,
        // it is not actually a pipeline script, but a deprecated programming exercise with an old build xml configuration
        if (!pipeLineScript.startsWith("pipeline") && !pipeLineScript.startsWith(PIPELINE_SCRIPT_DETECTION_COMMENT)) {
            throw new IllegalArgumentException("Pipeline Script not found");
        }
        // Replace repo URL
        // TODO: properly replace the baseRepoUrl with repoUrl by looking up the ciRepoName in the pipelineScript
        pipeLineScript = pipeLineScript.replace(baseRepoUrl, repoUrl);

        scriptNode.getFirstChild().setTextContent(pipeLineScript);
    }

    /**
     * Finds the script node within the xml document.
     *
     * @param xmlDocument the xml document
     * @return the script node or null
     */
    private static Node findScriptNode(Document xmlDocument) {
        final var userRemoteConfigs = xmlDocument.getElementsByTagName("script");
        return userRemoteConfigs.item(0);
    }

    /**
     * Replace old XML files that are not based on pipelines.
     * Will be removed in the future
     *
     * @param jobXmlDocument The Jenkins job config xml
     * @param repoUrl the repository url to add
     * @param repoNameInCI The repository name in the document
     */
    @Deprecated
    public static void replaceRemoteURLs(Document jobXmlDocument, String repoUrl, String repoNameInCI) throws IllegalArgumentException {
        final var remoteUrlNode = findUserRemoteConfigFor(jobXmlDocument, repoNameInCI);
        if (remoteUrlNode == null || remoteUrlNode.getFirstChild() == null) {
            throw new IllegalArgumentException("Url to replace not found in job xml document");
        }
        remoteUrlNode.getFirstChild().setNodeValue(repoUrl);
    }

    /**
     * Finds the user remote config from the specified Jenkins job config xml document.
     *
     * @param jobXmlDocument The Jenkins job config xml
     * @param repoNameInCI the name of the repository attached to the user remote config
     * @return the node containing the user remote config or null
     */
    private static Node findUserRemoteConfigFor(Document jobXmlDocument, String repoNameInCI) {
        final var userRemoteConfigs = jobXmlDocument.getElementsByTagName("hudson.plugins.git.UserRemoteConfig");
        if (userRemoteConfigs.getLength() != 2) {
            throw new IllegalArgumentException("Configuration of build plans currently only supports a model with two repositories, ASSIGNMENT and TESTS");
        }
        var firstUserRemoteConfig = userRemoteConfigs.item(0).getChildNodes();
        var urlElement = findUrlElement(firstUserRemoteConfig, repoNameInCI);
        if (urlElement != null) {
            return urlElement;
        }
        var secondUserRemoteConfig = userRemoteConfigs.item(1).getChildNodes();
        urlElement = findUrlElement(secondUserRemoteConfig, repoNameInCI);
        return urlElement;
    }

    /**
     * Finds the node that contains the repository name.
     *
     * @param nodeList list of nodes
     * @param repoNameInCI the repository name to find
     * @return the node that contains the repository name.
     */
    private static Node findUrlElement(NodeList nodeList, String repoNameInCI) {
        boolean found = false;
        Node urlNode = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            var childElement = nodeList.item(i);
            if ("name".equalsIgnoreCase(childElement.getNodeName())) {
                var nameValue = childElement.hasChildNodes() ? childElement.getFirstChild().getNodeValue() : null;
                // this name was added recently, so we cannot assume that all job xml files include this name
                if (repoNameInCI.equalsIgnoreCase(nameValue)) {
                    found = true;
                }
            }
            else if ("url".equalsIgnoreCase(childElement.getNodeName())) {
                urlNode = childElement;
                if (!found) {
                    // fallback for old xmls
                    var urlValue = childElement.hasChildNodes() ? childElement.getFirstChild().getNodeValue() : null;
                    if (urlValue != null && repoNameInCI.equals(ASSIGNMENT_REPO_NAME) && ((urlValue.contains("-exercise.git") || (urlValue.contains("-solution.git"))))) {
                        found = true;
                    }
                    else if (urlValue != null && repoNameInCI.equals(TEST_REPO_NAME) && urlValue.contains("-tests.git")) {
                        found = true;
                    }
                }
            }
        }

        if (found && urlNode != null) {
            return urlNode;
        }
        else {
            return null;
        }
    }
}
