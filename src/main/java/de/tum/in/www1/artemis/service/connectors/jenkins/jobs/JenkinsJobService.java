package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import java.io.IOException;
import java.io.StringWriter;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Service
@Profile("jenkins")
public class JenkinsJobService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsJobService.class);

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsServer jenkinsServer;

    public JenkinsJobService(JenkinsServer jenkinsServer) {
        this.jenkinsServer = jenkinsServer;
    }

    /**
     * Retrieves the job inside a folder job or null if it doesn't exist.
     * @param folderJobName the name of the folder job
     * @param jobName the name of the job
     * @return the job with details
     */
    public JobWithDetails getJobInFolder(String folderJobName, String jobName) {
        if (folderJobName == null || jobName == null) {
            log.warn("Cannot get the job, because projectKey " + folderJobName + " or jobName " + jobName + " is null");
            return null;
        }

        final var folder = getFolderJob(folderJobName);
        if (folder == null) {
            log.warn("Cannot get the job" + jobName + " in folder " + folderJobName + " because it doesn't exist.");
            return null;
        }

        try {
            return jenkinsServer.getJob(folder, jobName);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the folder job or null if it doesn't exist
     * @param folderName the name of the folder job
     * @return the folder job
     */
    public FolderJob getFolderJob(String folderName) {
        try {
            final var job = jenkinsServer.getJob(folderName);
            if (job == null) {
                return null;
            }

            final var folderJob = jenkinsServer.getFolderJob(job);
            if (!folderJob.isPresent()) {
                return null;
            }
            return folderJob.get();
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the xml config of the job that is inside a folder
     * @param folderName the name of the folder
     * @param jobName the name of the job
     * @return the xml document
     */
    public Document getJobConfigForJobInFolder(String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            if (folder == null) {
                throw new JenkinsException("The folder " + folderName + "does not exist.");
            }

            var xmlString = jenkinsServer.getJobXml(folder, jobName);
            return XmlFileUtils.readFromString(xmlString);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the xml config of the folder job.
     * @param folderName the name of the folder
     * @return the xml document or null if the folder doesn't exist
     * @throws IOException in case of errors
     */
    public org.jsoup.nodes.Document getFolderConfig(String folderName) throws IOException {
        if (jenkinsServer.getJob(folderName) == null) {
            return null;
        }

        var folderXml = jenkinsServer.getJobXml(folderName);

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(folderXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);

        return document;
    }

    /**
     * Creates a job inside a folder
     * @param jobConfig the config of the job to create
     * @param folderName the name of the folder
     * @param jobName the name of the job
     */
    public void createJobInFolder(Document jobConfig, String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            if (folder == null) {
                throw new JenkinsException("Cannot create job " + jobName + " because the folder " + folderName + " does not exist.");
            }
            jenkinsServer.createJob(folder, jobName, writeXmlToString(jobConfig), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Writes the xml document into a string.
     * @param doc the xml document
     * @return the xml as string
     */
    public String writeXmlToString(Document doc) {
        try {
            final var tf = TransformerFactory.newInstance();
            final var transformer = tf.newTransformer();
            final var writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.getBuffer().toString();
        }
        catch (TransformerException e) {
            final var errorMessage = "Unable to parse XML document to String! " + doc;
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }

    /**
     * Gets the job config of a job that is inside a folder
     * @param folderName the name of the folder
     * @param jobName the name of the job
     * @return the job config as an xml document or null if the job doesn't exist
     * @throws IOException in case of errors
     */
    public org.jsoup.nodes.Document getJobConfig(String folderName, String jobName) throws IOException {
        var job = jenkinsServer.getJob(folderName);
        if (job == null) {
            return null;
        }
        var folder = jenkinsServer.getFolderJob(job);
        var jobXml = jenkinsServer.getJobXml(folder.orNull(), jobName);

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(jobXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);
        return document;
    }

    /**
     * Updates a job.
     * @param folderName optional folder name where the job resides
     * @param jobName the name of the job
     * @param jobConfig the updated job config
     * @throws IOException in case of errors
     */
    public void updateJob(String folderName, String jobName, org.jsoup.nodes.Document jobConfig) throws IOException {
        if (folderName != null && !folderName.isEmpty()) {
            var job = jenkinsServer.getJob(folderName);
            var folder = jenkinsServer.getFolderJob(job);
            jenkinsServer.updateJob(folder.orNull(), jobName, jobConfig.toString(), useCrumb);
        }
        else {
            jenkinsServer.updateJob(jobName, jobConfig.toString(), useCrumb);
        }
    }
}
