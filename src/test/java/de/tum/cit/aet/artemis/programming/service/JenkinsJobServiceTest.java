package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsXmlFileUtils.getDocumentBuilderFactory;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.same;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.test.context.support.WithMockUser;
import org.w3c.dom.Document;

import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsXmlFileUtils;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;

class JenkinsJobServiceTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "jenkinsjobservicetest";

    private static MockedStatic<JenkinsXmlFileUtils> mockedXmlFileUtils;

    private Document invalidDocument;

    private Document validDocument;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        jenkinsRequestMockProvider.enableMockingOfRequests();
        // create the document before the mock so that it still works correctly
        invalidDocument = createEmptyDOMDocument();
        validDocument = createEmptyDOMDocument();

        // mock the file utils
        mockedXmlFileUtils = mockStatic(JenkinsXmlFileUtils.class);
        mockedXmlFileUtils.when(() -> JenkinsXmlFileUtils.writeToString(same(invalidDocument))).thenThrow(TransformerException.class);
        mockedXmlFileUtils.when(() -> JenkinsXmlFileUtils.writeToString(same(validDocument))).thenReturn("JenkinsConfigStringMock");
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
        mockedXmlFileUtils.close();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCreateIfJobExists() throws IOException {
        var job = new JenkinsJobService.JobWithDetails("JenkinsJob", "", false);
        jenkinsRequestMockProvider.mockGetJob("JenkinsFolder", "JenkinsJob", job, false);
        // This call shall not fail, since the job already exists ..
        jenkinsJobService.createJobInFolder(validDocument, "JenkinsFolder", "JenkinsJob");
        // Create Job shouldn't be invoked because it exists
        jenkinsRequestMockProvider.verifyMocks();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCreateIfJobDoesNotExist() throws IOException {
        jenkinsRequestMockProvider.mockGetJob("JenkinsFolder", "JenkinsJob", null, false);
        jenkinsRequestMockProvider.mockCreateJob("JenkinsFolder", "JenkinsJob");
        // This call shall not fail, since the job will be created ..
        jenkinsJobService.createJobInFolder(validDocument, "JenkinsFolder", "JenkinsJob");
        // Create Job should be invoked
        jenkinsRequestMockProvider.verifyMocks();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCreateJobInFolderJenkinsExceptionOnXmlError() throws IOException {
        jenkinsRequestMockProvider.mockCreateJobInFolder("JenkinsFolder", "JenkinsJob", false);
        assertThatExceptionOfType(JenkinsException.class).isThrownBy(() -> jenkinsJobService.createJobInFolder(invalidDocument, "JenkinsFolder", "JenkinsJob"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testUpdateJobThrowIOExceptionOnXmlError() {
        assertThatExceptionOfType(JenkinsException.class).isThrownBy(() -> jenkinsJobService.updateJob("JenkinsFolder", "JenkinsJob", invalidDocument));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testUpdateFolderJobThrowIOExceptionOnXmlError() {
        assertThatIOException().isThrownBy(() -> jenkinsJobService.updateFolderJob("JenkinsFolder", invalidDocument));
    }

    private Document createEmptyDOMDocument() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = getDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.newDocument();
    }
}
