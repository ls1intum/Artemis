package de.tum.cit.aet.artemis.core.service.connectors.jenkins.build_plan;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class JenkinsBuildPlanUtils {

    private static final String PIPELINE_SCRIPT_DETECTION_COMMENT = "// ARTEMIS: JenkinsPipeline";

    /**
     * Replaces either one of the previous repository uris or the build plan url written within the Jenkins pipeline
     * script with the value specified by newUrl.
     *
     * @param jobXmlDocument the Jenkins pipeline
     * @param previousUrl    the previous url that will be replaced
     * @param newUrl         the new repository or build plan url
     * @throws IllegalArgumentException if the xml document isn't a Jenkins pipeline script
     */
    public static void replaceScriptParameters(Document jobXmlDocument, String previousUrl, String newUrl) throws IllegalArgumentException {
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
        // Replace URL
        // TODO: properly replace the previousUrl with newUrl by looking up the ciRepoName in the pipelineScript
        pipeLineScript = pipeLineScript.replace(previousUrl, newUrl);

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
