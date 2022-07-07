package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.OnlineUnitRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class OnlineUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OnlineUnitRepository onlineUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Lecture lecture1;

    private OnlineUnit onlineUnit;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(1, 1, 0, 1);
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.onlineUnit = new OnlineUnit();
        this.onlineUnit.setDescription("LoremIpsum");
        this.onlineUnit.setName("LoremIpsum");
        this.onlineUnit.setSource("oHg5SJYRHA0");

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/online-units", onlineUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/online-units/0", HttpStatus.FORBIDDEN, OnlineUnit.class);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createOnlineUnit_asInstructor_shouldCreateOnlineUnit() throws Exception {
        onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        var persistedOnlineUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.CREATED);
        assertThat(persistedOnlineUnit.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void createOnlineUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/online-units", onlineUnit, OnlineUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateOnlineUnit_asInstructor_shouldUpdateOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        this.onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.onlineUnit.setDescription("Changed");
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.OK);
        assertThat(this.onlineUnit.getDescription()).isEqualTo("Changed");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateOnlineUnit_asInstructor_shouldKeepOrdering() throws Exception {
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
    public void updateOnlineUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        this.onlineUnit.setDescription("Changed");
        this.onlineUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateOnlineUnit_noId_shouldReturnBadRequest() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        this.onlineUnit.setId(null);
        this.onlineUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/online-units", this.onlineUnit, OnlineUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getOnlineUnit_correctId_shouldReturnOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        OnlineUnit onlineUnitFromRequest = request.get("/api/lectures/" + lecture1.getId() + "/online-units/" + this.onlineUnit.getId(), HttpStatus.OK, OnlineUnit.class);
        assertThat(this.onlineUnit.getId()).isEqualTo(onlineUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteOnlineUnit_correctId_shouldDeleteOnlineUnit() throws Exception {
        persistOnlineUnitWithLecture();

        this.onlineUnit = (OnlineUnit) lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId()).getLectureUnits().stream().findFirst().get();
        assertThat(this.onlineUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.onlineUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/online-units/" + this.onlineUnit.getId(), HttpStatus.NOT_FOUND, OnlineUnit.class);
    }

}
