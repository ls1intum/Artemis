package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.TextUnitRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class TextUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textunitintegrationtest";

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture;

    private TextUnit textUnit;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        this.lecture = lectureUtilService.createCourseWithLecture(true);
        this.textUnit = new TextUnit();
        this.textUnit.setName("LoremIpsum     ");
        this.textUnit.setContent("This is a Test");

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "editor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture.getId() + "/text-units", textUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture.getId() + "/text-units", textUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture.getId() + "/text-units/0", HttpStatus.FORBIDDEN, TextUnit.class);
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
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createTextUnit_asEditor_shouldCreateTextUnitUnit() throws Exception {
        var persistedTextUnit = request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.CREATED);
        assertThat(persistedTextUnit.getId()).isNotNull();
        assertThat(persistedTextUnit.getName()).isEqualTo("LoremIpsum");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor42", roles = "EDITOR")
    void createTextUnit_EditorNotInCourse_shouldReturnForbidden() throws Exception {
        request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.FORBIDDEN);
        request.postWithResponseBody("/api/lectures/" + "2379812738912" + "/text-units", textUnit, TextUnit.class, HttpStatus.NOT_FOUND);
        textUnit.setLecture(new Lecture());
        request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.BAD_REQUEST);
        textUnit.setId(21312321L);
        request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateTextUnit_asEditor_shouldUpdateTextUnit() throws Exception {
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        textUnitFromRequest.setContent("Changed");
        TextUnit updatedTextUnit = request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", textUnitFromRequest, TextUnit.class, HttpStatus.OK);
        assertThat(updatedTextUnit.getContent()).isEqualTo("Changed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateTextUnit_asEditor_shouldKeepOrdering() throws Exception {
        persistTextUnitWithLecture();

        // Add a second lecture unit
        TextUnit textUnit = lectureUtilService.createTextUnit();
        lecture.addLectureUnit(textUnit);
        lecture = lectureRepository.save(lecture);

        List<LectureUnit> orderedUnits = lecture.getLectureUnits();

        // Updating the lecture unit should not change order attribute
        request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.OK);

        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateTextUnit_noId_shouldReturnBadRequest() throws Exception {
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        textUnitFromRequest.setId(null);
        request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", textUnitFromRequest, TextUnit.class, HttpStatus.BAD_REQUEST);

        request.get("/api/lectures/" + "2379812738912" + "/text-units/" + this.textUnit.getId(), HttpStatus.BAD_REQUEST, TextUnit.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getTextUnit_correctId_shouldReturnTextUnit() throws Exception {
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        assertThat(this.textUnit.getId()).isEqualTo(textUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextUnit_correctId_shouldDeleteTextUnit() throws Exception {
        persistTextUnitWithLecture();
        assertThat(this.textUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture.getId() + "/lecture-units/" + this.textUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.NOT_FOUND, TextUnit.class);
    }

    private void persistTextUnitWithLecture() {
        this.textUnit = textUnitRepository.save(this.textUnit);
        lecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        lecture.addLectureUnit(this.textUnit);
        lecture = lectureRepository.save(lecture);
        this.textUnit = (TextUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
    }
}
