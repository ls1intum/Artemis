package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Objects;
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
import de.tum.cit.aet.artemis.lecture.dto.TextUnitDTO;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TextUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textunitintegrationtest";

    @Autowired
    private LectureTestRepository lectureRepository;

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
        this.textUnit.setName("LoremIpsum");
        this.textUnit.setContent("This is a Test");

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "editor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        competency = competencyUtilService.createCompetency(lecture.getCourse());
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lecture/lectures/" + lecture.getId() + "/text-units", TextUnitDTO.of(textUnit), HttpStatus.FORBIDDEN);
        request.post("/api/lecture/lectures/" + lecture.getId() + "/text-units", TextUnitDTO.of(textUnit), HttpStatus.FORBIDDEN);
        request.get("/api/lecture/lectures/" + lecture.getId() + "/text-units/0", HttpStatus.FORBIDDEN, TextUnitDTO.class);
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
        var persistedTextUnit = request.postWithResponseBody("/api/lecture/lectures/" + this.lecture.getId() + "/text-units", TextUnitDTO.of(textUnit), TextUnitDTO.class,
                HttpStatus.CREATED);
        assertThat(persistedTextUnit.id()).isNotNull();
        assertThat(persistedTextUnit.type()).isEqualTo("text");
        assertThat(persistedTextUnit.name()).isEqualTo("LoremIpsum");
        assertThat(persistedTextUnit.content()).isEqualTo(textUnit.getContent());
        assertThat(persistedTextUnit.competencyLinks()).hasSize(1);
        var competencyLink = persistedTextUnit.competencyLinks().stream().findFirst().orElseThrow();
        assertThat(competencyLink.competency().id()).isEqualTo(competency.getId());
        assertThat(competencyLink.weight()).isEqualTo(1);
        verify(competencyProgressApi)
                .updateProgressByLearningObjectAsync(argThat(unit -> unit instanceof TextUnit textUnit && Objects.equals(textUnit.getId(), persistedTextUnit.id())));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor42", roles = "EDITOR")
    void createTextUnit_EditorNotInCourse_shouldReturnForbidden() throws Exception {
        request.postWithResponseBody("/api/lecture/lectures/" + this.lecture.getId() + "/text-units", TextUnitDTO.of(textUnit), TextUnitDTO.class, HttpStatus.FORBIDDEN);
        request.postWithResponseBody("/api/lecture/lectures/" + "2379812738912" + "/text-units", TextUnitDTO.of(textUnit), TextUnitDTO.class, HttpStatus.FORBIDDEN);
        request.postWithResponseBody("/api/lecture/lectures/" + this.lecture.getId() + "/text-units", textUnitDtoWithId(21312321L), TextUnitDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateTextUnit_asEditor_shouldUpdateTextUnit() throws Exception {
        textUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, textUnit, 1)));
        persistTextUnitWithLecture();
        textUnit.setContent("Changed");
        TextUnitDTO updatedTextUnit = request.putWithResponseBody("/api/lecture/lectures/" + lecture.getId() + "/text-units", TextUnitDTO.of(textUnit), TextUnitDTO.class,
                HttpStatus.OK);
        assertThat(updatedTextUnit.type()).isEqualTo("text");
        assertThat(updatedTextUnit.name()).isEqualTo(textUnit.getName());
        assertThat(updatedTextUnit.content()).isEqualTo("Changed");
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(eq(Set.of(competency.getId())), eq(textUnit));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateTextUnit_asEditor_shouldKeepOrdering() throws Exception {
        persistTextUnitWithLecture();

        var databaseLecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        assertThat(databaseLecture.getLectureUnits()).hasSize(1);
        // Add a second lecture unit
        TextUnit secondTextUnit = lectureUtilService.createTextUnit(lecture);
        lecture.addLectureUnit(secondTextUnit);
        lecture = lectureRepository.save(lecture);

        assertThat(lecture.getLectureUnits()).hasSize(2);
        databaseLecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        assertThat(databaseLecture.getLectureUnits()).hasSize(2);

        List<LectureUnit> orderedUnits = lecture.getLectureUnits();

        // Updating the lecture unit should not change order attribute
        request.putWithResponseBody("/api/lecture/lectures/" + lecture.getId() + "/text-units", TextUnitDTO.of(secondTextUnit), TextUnitDTO.class, HttpStatus.OK);
        databaseLecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        assertThat(lecture.getLectureUnits()).hasSize(2);

        List<LectureUnit> updatedOrderedUnits = databaseLecture.getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateTextUnit_noId_shouldReturnBadRequest() throws Exception {
        persistTextUnitWithLecture();
        TextUnitDTO textUnitFromRequest = request.get("/api/lecture/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnitDTO.class);
        TextUnitDTO textUnitWithoutId = new TextUnitDTO(null, textUnitFromRequest.name(), textUnitFromRequest.releaseDate(), textUnitFromRequest.content(),
                textUnitFromRequest.competencyLinks(), null);
        request.putWithResponseBody("/api/lecture/lectures/" + lecture.getId() + "/text-units", textUnitWithoutId, TextUnitDTO.class, HttpStatus.BAD_REQUEST);

        request.get("/api/lecture/lectures/" + "2379812738912" + "/text-units/" + this.textUnit.getId(), HttpStatus.BAD_REQUEST, TextUnitDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getTextUnit_correctId_shouldReturnTextUnit() throws Exception {
        persistTextUnitWithLecture();
        TextUnitDTO textUnitFromRequest = request.get("/api/lecture/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.OK, TextUnitDTO.class);
        assertThat(textUnitFromRequest.id()).isEqualTo(this.textUnit.getId());
        assertThat(textUnitFromRequest.type()).isEqualTo("text");
        assertThat(textUnitFromRequest.name()).isEqualTo(this.textUnit.getName());
        assertThat(textUnitFromRequest.content()).isEqualTo(this.textUnit.getContent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextUnit_correctId_shouldDeleteTextUnit() throws Exception {
        textUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, textUnit, 1)));
        persistTextUnitWithLecture();
        assertThat(this.textUnit.getId()).isNotNull();
        request.delete("/api/lecture/lectures/" + lecture.getId() + "/lecture-units/" + this.textUnit.getId(), HttpStatus.OK);
        request.get("/api/lecture/lectures/" + lecture.getId() + "/text-units/" + this.textUnit.getId(), HttpStatus.FORBIDDEN, TextUnitDTO.class);
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(textUnit), eq(Optional.empty()));
    }

    private TextUnitDTO textUnitDtoWithId(Long id) {
        return new TextUnitDTO(id, textUnit.getName(), textUnit.getReleaseDate(), textUnit.getContent(), TextUnitDTO.of(textUnit).competencyLinks(), null);
    }

    private void persistTextUnitWithLecture() {
        lecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        assertThat(lecture.getLectureUnits()).isEmpty();
        lecture.addLectureUnit(textUnit);
        lecture = lectureRepository.save(lecture);
        assertThat(lecture.getLectureUnits()).hasSize(1);

        // use the saved text unit with id from now on
        textUnit = (TextUnit) lecture.getLectureUnits().getFirst();

        lecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture.getId()).orElseThrow();
        assertThat(lecture.getLectureUnits()).hasSize(1);

        assertThat(textUnit.getLecture()).isNotNull();
        assertThat(textUnit.getLecture().getId()).isEqualTo(lecture.getId());
    }
}
