package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.XmlFileUtils.getDocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.w3c.dom.Document;

import com.offbytwo.jenkins.model.FolderJob;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

public class JenkinsJobServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private JenkinsJobService jenkinsJobService;

    private static MockedStatic<XmlFileUtils> mockedXmlFileUtils;

    private Document invalidDocument;

    private Document validDocument;

    @BeforeEach
    public void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        // create the document before the mock so that it still works correctly
        invalidDocument = createEmptyDOMDocument();
        validDocument = createEmptyDOMDocument();

        // mock the file utils
        mockedXmlFileUtils = mockStatic(XmlFileUtils.class);
        mockedXmlFileUtils.when(() -> XmlFileUtils.writeToString(same(invalidDocument))).thenThrow(TransformerException.class);
        mockedXmlFileUtils.when(() -> XmlFileUtils.writeToString(same(validDocument))).thenReturn("JenkinsConfigStringMock");
    }

    @AfterEach
    public void tearDown() throws IOException {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        mockedXmlFileUtils.close();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testCreateIfJobExists() throws IOException {
        jenkinsRequestMockProvider.mockCreateJobInFolder("JenkinsFolder", "JenkinsJob", true);
        // This call shall not fail, since the job already exists ..
        jenkinsJobService.createJobInFolder(validDocument, "JenkinsFolder", "JenkinsJob");
        // Create Job shouldn't be invoked on JenkinsServer because it exists
        verify(jenkinsServer, never()).createJob(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "student1")
    public void testCreateIfJobDoesNotExist() throws IOException {
        jenkinsRequestMockProvider.mockCreateJobInFolder("JenkinsFolder", "JenkinsJob", false);
        // This call shall not fail, since the job will be created ..
        jenkinsJobService.createJobInFolder(validDocument, "JenkinsFolder", "JenkinsJob");
        // Create Job should be invoked on JenkinsServer
        verify(jenkinsServer, times(1)).createJob(any(FolderJob.class), eq("JenkinsJob"), eq("JenkinsConfigStringMock"), any());
    }

    @Test
    @WithMockUser(username = "student1")
    public void testCreateJobInFolderJenkinsExceptionOnXmlError() throws IOException {
        jenkinsRequestMockProvider.mockGetFolderJob("JenkinsFolder", new FolderJob());
        assertThrows(JenkinsException.class, () -> jenkinsJobService.createJobInFolder(invalidDocument, "JenkinsFolder", "JenkinsJob"));
    }

    @Test
    @WithMockUser(username = "student1")
    public void testUpdateJobThrowIOExceptionOnXmlError() {
        assertThrows(IOException.class, () -> jenkinsJobService.updateJob("JenkinsFolder", "JenkinsJob", invalidDocument));
    }

    @Test
    @WithMockUser(username = "student1")
    public void testUpdateFolderJobThrowIOExceptionOnXmlError() {
        assertThrows(IOException.class, () -> jenkinsJobService.updateFolderJob("JenkinsFolder", invalidDocument));
    }

    private Document createEmptyDOMDocument() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = getDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.newDocument();
    }
}
