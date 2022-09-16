package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.OnlineUnitRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.OnlineResourceDTO;

class OnlineUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OnlineUnitRepository onlineUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Lecture lecture1;

    private OnlineUnit onlineUnit;

    private MockedStatic<Jsoup> jsoupMock;

    @BeforeEach
    void initTestCase() throws Exception {
        this.database.addUsers(1, 1, 0, 1);
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.onlineUnit = new OnlineUnit();
        this.onlineUnit.setDescription("LoremIpsum");
        this.onlineUnit.setName("LoremIpsum");
        this.onlineUnit.setSource("oHg5SJYRHA0");

        // Add users that are not in the course
        database.createAndSaveUser("student42");
        database.createAndSaveUser("tutor42");
        database.createAndSaveUser("instructor42");

        jsoupMock = mockStatic(Jsoup.class);
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/online-units/0", HttpStatus.FORBIDDEN, OnlineUnit.class);
    }

    @AfterEach
    void resetDatabase() {
        jsoupMock.close();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createOnlineUnit_asInstructor_shouldCreateOnlineUnit() throws Exception {
        onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        var persistedOnlineUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.CREATED);
        assertThat(persistedOnlineUnit.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void createOnlineUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_asInstructor_shouldUpdateOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        this.onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.onlineUnit.setDescription("Changed");
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.OK);
        assertThat(this.onlineUnit.getDescription()).isEqualTo("Changed");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistOnlineUnitWithLecture();

        // Add a second lecture unit
        OnlineUnit onlineUnit = database.createOnlineUnit();
        lecture1.addLectureUnit(onlineUnit);
        lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not change order attribute
        request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.OK);

        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistOnlineUnitWithLecture() {
        this.onlineUnit = onlineUnitRepository.save(this.onlineUnit);
        lecture1.addLectureUnit(this.onlineUnit);
        lecture1 = lectureRepository.save(lecture1);
        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void updateOnlineUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        this.onlineUnit.setDescription("Changed");
        this.onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateOnlineUnit_noId_shouldReturnBadRequest() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        this.onlineUnit.setId(null);
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getOnlineUnit_correctId_shouldReturnOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        OnlineUnit onlineUnitFromRequest = request.get("/api/lectures/" + lecture1.getId() + "/online-units/" + this.onlineUnit.getId(), HttpStatus.OK, OnlineUnit.class);
        assertThat(this.onlineUnit.getId()).isEqualTo(onlineUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void getOnlineResource() throws Exception {
        String url = "https://www.google.de";
        var connectionMock = mock(Connection.class);
        jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connectionMock);
        when(connectionMock.timeout(anyInt())).thenReturn(connectionMock);
        when(connectionMock.maxBodySize(anyInt())).thenReturn(connectionMock);
        when(connectionMock.get()).thenReturn(new Document(url));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("link", url);
        OnlineResourceDTO onlineResourceDTO = request.get("/api/lectures/online-units/fetch-online-resource", HttpStatus.OK, OnlineResourceDTO.class, params);
        assertThat(onlineResourceDTO.url()).isEqualTo(url);
        assertThat(onlineResourceDTO.title()).isNull();
        assertThat(onlineResourceDTO.description()).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteOnlineUnit_correctId_shouldDeleteOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        assertThat(this.onlineUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.onlineUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/online-units/" + this.onlineUnit.getId(), HttpStatus.NOT_FOUND, OnlineUnit.class);
    }

}
