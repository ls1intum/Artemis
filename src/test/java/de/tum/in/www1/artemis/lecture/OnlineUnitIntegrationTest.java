package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.util.List;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.OnlineUnitRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.OnlineResourceDTO;

class OnlineUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "onlineunitintegration";

    @Autowired
    private OnlineUnitRepository onlineUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture1;

    private OnlineUnit onlineUnit;

    private MockedStatic<Jsoup> jsoupMock;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        this.onlineUnit = new OnlineUnit();
        this.onlineUnit.setDescription("LoremIpsum");
        this.onlineUnit.setName("LoremIpsum");
        this.onlineUnit.setSource("oHg5SJYRHA0");

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        jsoupMock = mockStatic(Jsoup.class);
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/online-units/0", HttpStatus.FORBIDDEN, OnlineUnit.class);
    }

    @AfterEach
    void tearDown() {
        jsoupMock.close();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createOnlineUnit_asInstructor_shouldCreateOnlineUnit() throws Exception {
        onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        var persistedOnlineUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.CREATED);
        assertThat(persistedOnlineUnit.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createOnlineUnit_invalidUrl_shouldReturnBadRequest() throws Exception {
        onlineUnit.setSource("abc123");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createOnlineUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createOnlineUnit_withId_shouldReturnBadRequest() throws Exception {
        onlineUnit.setId(999L);
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_asInstructor_shouldUpdateOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        this.onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.onlineUnit.setDescription("Changed");
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.OK);
        assertThat(this.onlineUnit.getDescription()).isEqualTo("Changed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistOnlineUnitWithLecture();

        // Add a second lecture unit
        OnlineUnit onlineUnit = lectureUtilService.createOnlineUnit();
        lecture1.addLectureUnit(onlineUnit);
        lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not change order attribute
        request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.OK);

        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistOnlineUnitWithLecture() {
        this.onlineUnit = onlineUnitRepository.save(this.onlineUnit);
        lecture1.addLectureUnit(this.onlineUnit);
        lecture1 = lectureRepository.save(lecture1);
        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateOnlineUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        this.onlineUnit.setDescription("Changed");
        this.onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_noId_shouldReturnBadRequest() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        this.onlineUnit.setId(null);
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_noLecture_shouldReturnBadRequest() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        this.onlineUnit.setLecture(null);
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOnlineUnit_correctId_shouldReturnOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        OnlineUnit onlineUnitFromRequest = request.get("/api/lectures/" + lecture1.getId() + "/online-units/" + this.onlineUnit.getId(), HttpStatus.OK, OnlineUnit.class);
        assertThat(this.onlineUnit.getId()).isEqualTo(onlineUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOnlineUnit_incorrectId_shouldReturnBadRequest() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        request.get("/api/lectures/" + "999" + "/online-units/" + this.onlineUnit.getId(), HttpStatus.BAD_REQUEST, OnlineUnit.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "https://www.google.de", "HTTP://example.com:80?query=1" })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getOnlineResource(String link) throws Exception {
        var url = new URL(link).toString();
        var connectionMock = mock(Connection.class);
        jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connectionMock);
        when(connectionMock.timeout(anyInt())).thenReturn(connectionMock);
        when(connectionMock.maxBodySize(anyInt())).thenReturn(connectionMock);
        when(connectionMock.get()).thenReturn(new Document(url));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("link", link);
        OnlineResourceDTO onlineResourceDTO = request.get("/api/lectures/online-units/fetch-online-resource", HttpStatus.OK, OnlineResourceDTO.class, params);
        assertThat(onlineResourceDTO.url()).isEqualTo(url);
        assertThat(onlineResourceDTO.title()).isNull();
        assertThat(onlineResourceDTO.description()).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(strings = { "abc", "123", "file://", "ftp://", "http://127.0.0.1", "http://localhost:80" })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getOnlineResource_malformedUrl(String link) throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("link", link);
        request.get("/api/lectures/online-units/fetch-online-resource", HttpStatus.BAD_REQUEST, OnlineResourceDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteOnlineUnit_correctId_shouldDeleteOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().orElseThrow();
        assertThat(this.onlineUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.onlineUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/online-units/" + this.onlineUnit.getId(), HttpStatus.NOT_FOUND, OnlineUnit.class);
    }

}
