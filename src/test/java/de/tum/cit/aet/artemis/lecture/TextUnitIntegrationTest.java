package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TextUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textunitintegrationtest";

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    private Lecture lecture;

    private TextUnit textUnit;

    private Competency competency;

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

        competency = competencyUtilService.createCompetency(lecture.getCourse());
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
        textUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, textUnit, 1)));
        var persistedTextUnit = request.postWithResponseBody("/api/lectures/" + this.lecture.getId() + "/text-units", textUnit, TextUnit.class, HttpStatus.CREATED);
        assertThat(persistedTextUnit.getId()).isNotNull();
        assertThat(persistedTextUnit.getName()).isEqualTo("LoremIpsum");
        verify(competencyProgressService).updateProgressByLearningObjectAsync(eq(persistedTextUnit));
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
        textUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, textUnit, 1)));
        persistTextUnitWithLecture();
        TextUnit textUnitFromRequest = request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnit.class);
        textUnitFromRequest.setContent("Changed");
        TextUnit updatedTextUnit = request.putWithResponseBody("/api/lectures/" + lecture.getId() + "/text-units", textUnitFromRequest, TextUnit.class, HttpStatus.OK);
        assertThat(updatedTextUnit.getContent()).isEqualTo("Changed");
        verify(competencyProgressService, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(textUnit), eq(Optional.of(textUnit)));
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
        textUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, textUnit, 1)));
        persistTextUnitWithLecture();
        assertThat(this.textUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture.getId() + "/lecture-units/" + this.textUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.NOT_FOUND, TextUnit.class);
        verify(competencyProgressService, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(textUnit), eq(Optional.empty()));
    }

    private void persistTextUnitWithLecture() {
        this.textUnit = textUnitRepository.save(this.textUnit);
        lecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        lecture.addLectureUnit(this.textUnit);
        lecture = lectureRepository.save(lecture);
        this.textUnit = (TextUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
    }
}
