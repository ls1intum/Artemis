package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.XmlFileUtils.getDocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

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

    private Document document;

    @BeforeEach
    public void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        // create the document before the mock so that it still works correctly
        document = createEmptyDOMDocument();

        // mock the file utils
        mockedXmlFileUtils = mockStatic(XmlFileUtils.class);
        mockedXmlFileUtils.when(() -> XmlFileUtils.writeToString(eq(document))).thenThrow(TransformerException.class);
    }

    @AfterEach
    public void tearDown() throws IOException {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        mockedXmlFileUtils.close();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testCreateJobInFolderJenkinsExceptionOnXmlError() throws IOException {
        jenkinsRequestMockProvider.mockGetFolderJob("JenkinsFolder", new FolderJob());
        assertThrows(JenkinsException.class, () -> jenkinsJobService.createJobInFolder(document, "JenkinsFolder", "JenkinsJob"));
    }

    @Test
    @WithMockUser(username = "student1")
    public void testUpdateJobThrowIOExceptionOnXmlError() {
        assertThrows(IOException.class, () -> jenkinsJobService.updateJob("JenkinsFolder", "JenkinsJob", document));
    }

    @Test
    @WithMockUser(username = "student1")
    public void testUpdateFolderJobThrowIOExceptionOnXmlError() {
        assertThrows(IOException.class, () -> jenkinsJobService.updateFolderJob("JenkinsFolder", document));
    }

    private Document createEmptyDOMDocument() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = getDocumentBuilderFactory();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        return builder.newDocument();
    }
}
