package de.tum.in.www1.artemis.lecture;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

public class LectureUnitProcessingIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "lectureunitprocessingintegration";

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private List<LectureUnitSplitDTO> lectureUnitSplits;

    private Lecture lecture1;

    private Attachment attachment;

    private AttachmentUnit attachmentUnit;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void initTestCase() {
        this.database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lectureUnitSplits = Collections.singletonList(new LectureUnitSplitDTO("Unit Name", ZonedDateTime.now(), 1, 20, 20));
        this.attachment = ModelFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("files/temp/example.txt");
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.attachmentUnit = new AttachmentUnit();
        this.attachmentUnit.setDescription("Lorem Ipsum");

        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "student42");
        database.createAndSaveUser(TEST_PREFIX + "tutor42");
        database.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testAll_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.getMvc().perform(buildSplitAndCreateAttachmentUnits(lectureUnitSplits, "Hello World")).andExpect(status().isForbidden());
        request.getMvc().perform(buildGetSplitInformation("Hello World")).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder buildSplitAndCreateAttachmentUnits(@NotNull List<LectureUnitSplitDTO> lectureUnitSplits, @NotNull String fileContent) throws Exception {
        var lectureUnitSplitPart = new MockMultipartFile("lectureUnitSplitDTOs", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(lectureUnitSplits).getBytes());
        var filePart = new MockMultipartFile("file", "testFile.pdf", MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes(StandardCharsets.UTF_8));

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/attachment-units/split").file(lectureUnitSplitPart).file(filePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockHttpServletRequestBuilder buildGetSplitInformation(@NotNull String fileContent) {
        var filePart = new MockMultipartFile("file", "testFile.pdf", MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes(StandardCharsets.UTF_8));

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/process-units").file(filePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

}
