package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

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
}
