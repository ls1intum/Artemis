package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.TextUnitRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TextUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Lecture lecture;

    private TextUnit textUnit;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(1, 1, 1, 1);
        this.lecture = this.database.createCourseWithLecture(true);
        this.textUnit = new TextUnit();
        this.textUnit.setName("LoremIpsum");
        this.textUnit.setContent("This is a Test");

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture.getId() + "/text-units", textUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture.getId() + "/text-units", textUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture.getId() + "/text-units/0", HttpStatus.FORBIDDEN, TextUnit.class);
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
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void createTextUnit_asEditor_shouldCreateTextUnitUnit() throws Exception {
        var persistedTextUnit = request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.CREATED);
        assertThat(persistedTextUnit.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "editor42", roles = "EDITOR")
    public void createTextUnit_EditorNotInCourse_shouldReturnForbidden() throws Exception {
        request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.FORBIDDEN);
        request.postWithResponseBody("/api/lectures/" + "2379812738912" + "/text-units", textUnit, TextUnit.class, HttpStatus.BAD_REQUEST);
        textUnit.setLecture(new Lecture());
        request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.CONFLICT);
        textUnit.setId(21312321L);
        request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updateTextUnit_asEditor_shouldUpdateTextUnit() throws Exception {
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        textUnitFromRequest.setContent("Changed");
        TextUnit updatedTextUnit = request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", textUnitFromRequest, TextUnit.class, HttpStatus.OK);
        assertThat(updatedTextUnit.getContent()).isEqualTo("Changed");
        this.textUnit.setLecture(null);
        request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", this.textUnit, TextUnit.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updateTextUnit_noId_shouldReturnBadRequest() throws Exception {
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        textUnitFromRequest.setId(null);
        request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", textUnitFromRequest, TextUnit.class, HttpStatus.BAD_REQUEST);

        request.get("/api/lectures/" + "2379812738912" + "/text-units/" + this.textUnit.getId(), HttpStatus.CONFLICT, TextUnit.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getTextUnit_correctId_shouldReturnTextUnit() throws Exception {
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        assertThat(this.textUnit.getId()).isEqualTo(textUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteTextUnit_correctId_shouldDeleteTextUnit() throws Exception {
        persistTextUnitWithLecture();
        assertThat(this.textUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture.getId() + "/lecture-units/" + this.textUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.NOT_FOUND, TextUnit.class);
    }

    private void persistTextUnitWithLecture() {
        this.textUnit = textUnitRepository.save(this.textUnit);
        lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture.getId()).get();
        lecture.addLectureUnit(this.textUnit);
        lecture = lectureRepository.save(lecture);
        this.textUnit = (TextUnit) lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture.getId()).get().getLectureUnits().stream().findFirst().get();
    }
}
