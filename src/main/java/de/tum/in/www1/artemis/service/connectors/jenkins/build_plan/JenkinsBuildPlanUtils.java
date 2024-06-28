package de.tum.in.www1.artemis.service.connectors.jenkins.build_plan;

import java.util.function.Function;

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
        // TODO: properly replace the previousUrl with newUrl by looking up the ciRepoName in the pipelineScript
        editPipelineScript(jobXmlDocument, pipelineScript -> pipelineScript.replace(previousUrl, newUrl));
    }

    /**
     * Replace every occurrence within the Jenkins pipeline script of the old text through the new text
     *
     * @param jobXmlDocument the Jenkins pipeline
     * @param oldText        old text which should be replaced
     * @param newText        new text to replace the old one
     * @throws IllegalArgumentException
     */
    public static void searchAndReplaceInScript(Document jobXmlDocument, String oldText, String newText) throws IllegalArgumentException {
        editPipelineScript(jobXmlDocument, pipelineScript -> pipelineScript.replace(oldText, newText));
    }

    private static void editPipelineScript(Document jobXmlDocument, Function<String, String> editor) throws IllegalArgumentException {
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
        // perform editing
        pipeLineScript = editor.apply(pipeLineScript);

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
