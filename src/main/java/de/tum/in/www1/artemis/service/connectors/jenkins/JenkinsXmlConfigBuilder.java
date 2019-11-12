package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;

import org.w3c.dom.Document;

public interface JenkinsXmlConfigBuilder {

    Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL);
}
