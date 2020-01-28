package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;

import org.w3c.dom.Document;

public interface JenkinsXmlConfigBuilder {

    /**
     * Creates a basic build config for Jenkins based on the given repository URLs. I.e. a build that tests the assignemnt
     * code and exports the build results to Artemis afterwards. This will build a default config without sequential
     * test runs
     *
     * @param testRepositoryURL The URL of the repository containing all exercise tests
     * @param assignmentRepositoryURL The URL of the assignment repository, i.e. template or participation repo
     * @return The parsed XML doxument containing the Jenkins build config
     * @throws IOException If the template XML build config could not be read
     */
    Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL) throws IOException;

    /**
     * Creates a basic build config for Jenkins based on the given repository URLs. I.e. a build that tests the assignemnt
     * code and exports the build results to Artemis afterwards.
     *
     * @param testRepositoryURL The URL of the repository containing all exercise tests
     * @param assignmentRepositoryURL The URL of the assignment repository, i.e. template or participation repo
     * @param isSequential Whether the build should support sequential test runs or not
     * @return The parsed XML doxument containing the Jenkins build config
     * @throws IOException If the template XML build config could not be read
     */
    Document buildBasicConfig(URL testRepositoryURL, URL assignmentRepositoryURL, boolean isSequential) throws IOException;
}
