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
import de.tum.in.www1.artemis.exception.JenkinsJobNotFoundException;
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

    public JobWithDetails getJobInFolder(String folderJobName, String jobName) {
        if (folderJobName == null || jobName == null) {
            log.warn("Cannot get the job, because projectKey " + folderJobName + " or jobName " + jobName + " is null");
            return null;
        }
        final var folder = getFolderJob(folderJobName);
        try {
            return jenkinsServer.getJob(folder, jobName);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    public FolderJob getFolderJob(String folderName) {
        try {
            final var job = jenkinsServer.getJob(folderName);
            if (job == null) {
                throw new JenkinsException("The job " + folderName + " does not exist!");
            }
            final var folderJob = jenkinsServer.getFolderJob(job);
            if (!folderJob.isPresent()) {
                throw new JenkinsException("Folder " + folderName + " does not exist!");
            }
            return folderJob.get();
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    public Document getJobConfigForJobInFolder(String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            var xmlString = jenkinsServer.getJobXml(folder, jobName);
            return XmlFileUtils.readFromString(xmlString);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    public org.jsoup.nodes.Document getFolderConfig(String folderName) throws JenkinsJobNotFoundException, IOException {
        if (jenkinsServer.getJob(folderName) == null) {
            throw new JenkinsJobNotFoundException("The job " + folderName + " does not exist.");
        }

        var folderXml = jenkinsServer.getJobXml(folderName);

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(folderXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);

        return document;
    }

    public void createJobInFolder(Document jobConfig, String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            jenkinsServer.createJob(folder, jobName, writeXmlToString(jobConfig), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

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

    public org.jsoup.nodes.Document getJobConfig(String folderName, String jobName) throws JenkinsJobNotFoundException, IOException {
        var job = jenkinsServer.getJob(folderName);
        if (job == null) {
            throw new JenkinsJobNotFoundException("The job " + folderName + " does not exist.");
        }
        var folder = jenkinsServer.getFolderJob(job);
        var jobXml = jenkinsServer.getJobXml(folder.orNull(), jobName);

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(jobXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);
        return document;
    }

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
