package de.tum.cit.aet.artemis.atlas.competency;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

abstract class AbstractCompetencyPrerequisiteIntegrationTest extends AbstractAtlasIntegrationTest {

    protected Course course;

    protected Course course2;

    protected CourseCompetency courseCompetency;

    protected Lecture lecture;

    protected TextUnit textUnitOfLectureOne;

    protected AttachmentUnit attachmentUnitOfLectureOne;

    protected TextExercise teamTextExercise;

    protected TextExercise textExercise;

    // BeforeEach
    void setupTestScenario(String TEST_PREFIX, Function<Course, CourseCompetency> createCourseCompetencyForCourse) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        // creating course
        course = courseUtilService.createCourse();
        course2 = courseUtilService.createCourse();

        courseCompetency = createCourseCompetencyForCourse.apply(course);
        lecture = createLecture(course);

        textExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(courseCompetency), false);
        teamTextExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(courseCompetency), true);

        creatingLectureUnitsOfLecture(courseCompetency);
    }

    CompetencyRelation createRelation(CourseCompetency tail, CourseCompetency head, RelationType type) {
        CompetencyRelation relation = new CompetencyRelation();
        relation.setHeadCompetency(head);
        relation.setTailCompetency(tail);
        relation.setType(type);
        return competencyRelationRepository.save(relation);
    }

    void creatingLectureUnitsOfLecture(CourseCompetency competency) {
        // creating lecture units for lecture one
        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureOne");
        textUnit.setCompetencies(Set.of(competency));
        textUnit = textUnitRepository.save(textUnit);
        textUnitOfLectureOne = textUnit;

        AttachmentUnit attachmentUnit = lectureUtilService.createAttachmentUnit(true);
        attachmentUnit.setName("AttachmentUnitOfLectureOne");
        attachmentUnit.setCompetencies(Set.of(competency));
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        attachmentUnitOfLectureOne = attachmentUnit;

        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        exerciseUnitRepository.save(textExerciseUnit);

        ExerciseUnit teamTextExerciseUnit = new ExerciseUnit();
        teamTextExerciseUnit.setExercise(teamTextExercise);
        exerciseUnitRepository.save(teamTextExerciseUnit);

        for (LectureUnit lectureUnit : List.of(textUnit, attachmentUnit, textExerciseUnit, teamTextExerciseUnit)) {
            lecture.addLectureUnit(lectureUnit);
        }

        lectureRepository.save(lecture);
    }

    Lecture createLecture(Course course) {
        Lecture lecture = new Lecture();
        lecture.setTitle("LectureOne");
        lecture.setCourse(course);
        lectureRepository.save(lecture);

        return lecture;
    }

    TextExercise createTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assassmentDueDate, Set<CourseCompetency> competencies, boolean isTeamExercise) {
        // creating text exercise with Result
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assassmentDueDate, course);

        if (isTeamExercise) {
            textExercise.setMode(ExerciseMode.TEAM);
            TeamAssignmentConfig teamAssignmentConfig = new TeamAssignmentConfig();
            teamAssignmentConfig.setMinTeamSize(1);
            teamAssignmentConfig.setMaxTeamSize(2);
            textExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        }

        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setCompetencies(competencies);

        return exerciseRepository.save(textExercise);
    }

    private ProgrammingExercise createProgrammingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate) {
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(releaseDate, dueDate, course, ProgrammingLanguage.JAVA);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExercise.setCompetencies(Set.of(courseCompetency));
        return exerciseRepository.save(programmingExercise);
    }

    abstract CourseCompetency getCall(long courseId, long competencyId, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldReturnCompetencyForStudent() throws Exception {
        CourseCompetency response = getCall(course.getId(), courseCompetency.getId(), HttpStatus.OK);
        assertThat(response.getId()).isEqualTo(courseCompetency.getId());
    }

    // Test
    void testShouldOnlySendUserSpecificData(String TEST_PREFIX) throws Exception {
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        competencyProgressUtilService.createCompetencyProgress(courseCompetency, student1, 0, 1);

        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        competencyProgressUtilService.createCompetencyProgress(courseCompetency, student2, 10, 1);

        final var textUnit = textUnitRepository.findById(textUnitOfLectureOne.getId()).get();
        lectureUtilService.completeLectureUnitForUser(textUnit, student2);

        CourseCompetency response = getCall(course.getId(), courseCompetency.getId(), HttpStatus.OK);
        assertThat(response.getId()).isEqualTo(courseCompetency.getId());

        // only progress of student1 is fetched
        assertThat(response.getUserProgress()).hasSize(1);

        // only student2 has completed the textUnit
        assertThat(response.getLectureUnits().stream().findFirst().get().getCompletedUsers()).isEmpty();
    }

    // Test
    void shouldReturnForbiddenForUserNotInCourse() throws Exception {
        getCall(course.getId(), courseCompetency.getId(), HttpStatus.FORBIDDEN);
    }

    // Test
    void shouldReturnBadRequestForWrongCourse() throws Exception {
        getCall(course2.getId(), courseCompetency.getId(), HttpStatus.BAD_REQUEST);
    }

    abstract List<? extends CourseCompetency> getAllCall(long courseId, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldReturnCompetenciesForCourse(CourseCompetency newCompetency) throws Exception {
        TextUnit unreleasedLectureUnit = new TextUnit();
        unreleasedLectureUnit.setName("TextUnitOfLectureOne");
        unreleasedLectureUnit.setReleaseDate(ZonedDateTime.now().plusDays(5));
        unreleasedLectureUnit = textUnitRepository.save(unreleasedLectureUnit);
        lecture.addLectureUnit(unreleasedLectureUnit);
        lectureRepository.save(lecture);

        newCompetency.setTitle("Title");
        newCompetency.setDescription("Description");
        newCompetency.setCourse(course);
        newCompetency.setLectureUnits(new HashSet<>(List.of(unreleasedLectureUnit)));
        courseCompetencyRepository.save(newCompetency);

        List<? extends CourseCompetency> competenciesOfCourse = getAllCall(course.getId(), HttpStatus.OK);

        assertThat(competenciesOfCourse).anyMatch(c -> c.getId().equals(courseCompetency.getId()));
        assertThat(competenciesOfCourse.stream().filter(l -> l.getId().equals(newCompetency.getId())).findFirst().orElseThrow().getLectureUnits()).isEmpty();
    }

    // Test
    void testShouldReturnForbiddenForStudentNotInCourse() throws Exception {
        getAllCall(course.getId(), HttpStatus.FORBIDDEN);
    }

    abstract void deleteCall(long courseId, long competencyId, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldDeleteCompetencyWhenInstructor() throws Exception {
        deleteCall(course.getId(), courseCompetency.getId(), HttpStatus.OK);
        getCall(course.getId(), courseCompetency.getId(), HttpStatus.NOT_FOUND);
    }

    // Test
    void shouldDeleteCompetencyAndRelations(CourseCompetency competency2) throws Exception {
        createRelation(courseCompetency, competency2, RelationType.EXTENDS);

        deleteCall(course.getId(), courseCompetency.getId(), HttpStatus.OK);

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        assertThat(relations).isEmpty();
    }

    // Test
    void shouldReturnForbiddenForInstructorOfOtherCourseForDelete() throws Exception {
        deleteCall(course.getId(), courseCompetency.getId(), HttpStatus.FORBIDDEN);
    }

    // Test
    void deleteCourseShouldAlsoDeleteCompetencyAndRelations(CourseCompetency competency2) throws Exception {
        CompetencyRelation relation = createRelation(courseCompetency, competency2, RelationType.EXTENDS);
        Prerequisite prerequisite = prerequisiteUtilService.createPrerequisite(course);

        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(courseCompetencyRepository.existsById(courseCompetency.getId())).isFalse();
        assertThat(courseCompetencyRepository.existsById(competency2.getId())).isFalse();
        assertThat(competencyRelationRepository.existsById(relation.getId())).isFalse();
        assertThat(prerequisiteRepository.existsById(prerequisite.getId())).isFalse();
    }

    // Test
    void deleteLectureShouldUpdateCompetency() throws Exception {
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.OK);

        CourseCompetency result = getCall(course.getId(), courseCompetency.getId(), HttpStatus.OK);
        assertThat(result.getLectureUnits()).isEmpty();
    }

    // Test
    void deleteLectureUnitShouldUpdateCompetency() throws Exception {
        request.delete("/api/lectures/" + lecture.getId() + "/lecture-units/" + textUnitOfLectureOne.getId(), HttpStatus.OK);
        CourseCompetency result = getCall(course.getId(), courseCompetency.getId(), HttpStatus.OK);
        assertThat(result.getLectureUnits()).map(LectureUnit::getId).containsExactly(attachmentUnitOfLectureOne.getId());
    }

    abstract CourseCompetency updateCall(long courseId, CourseCompetency competency, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldUpdateCompetency() throws Exception {
        LectureUnit textLectureUnit = lectureUnitRepository.findByIdWithCompetenciesBidirectionalElseThrow(textUnitOfLectureOne.getId());
        courseCompetency.setTitle("Updated");
        courseCompetency.removeLectureUnit(textLectureUnit);
        courseCompetency.setDescription("Updated Description");

        CourseCompetency result = updateCall(course.getId(), courseCompetency, HttpStatus.OK);

        assertThat(result.getTitle()).isEqualTo("Updated");
        assertThat(result.getDescription()).isEqualTo("Updated Description");
        assertThat(result.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toSet())).doesNotContain(textLectureUnit.getId());
    }

    // Test
    void shouldReturnBadRequestForCompetencyWithoutId() throws Exception {
        courseCompetency.setId(null);
        updateCall(course.getId(), courseCompetency, HttpStatus.BAD_REQUEST);
    }

    // Test
    void shouldUpdateCompetencyToOptionalWhenSettingOptional(CourseCompetency newCompetency, IncludedInOverallScore includedInOverallScore) throws Exception {
        newCompetency.setTitle("Title");
        newCompetency.setDescription("Description");
        newCompetency.setCourse(course);
        newCompetency.setMasteryThreshold(42);
        newCompetency = courseCompetencyRepository.save(newCompetency);

        TextExercise exercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
        exercise.setMaxPoints(1.0);
        exercise.setIncludedInOverallScore(includedInOverallScore);
        exercise.setCompetencies(Set.of(newCompetency));
        exerciseRepository.save(exercise);

        newCompetency.setOptional(true);
        updateCall(course.getId(), newCompetency, HttpStatus.OK);

        CourseCompetency savedCompetency = courseCompetencyRepository.findByIdElseThrow(newCompetency.getId());
        assertThat(savedCompetency.isOptional()).isTrue();
    }

    abstract CourseCompetency createCall(long courseId, CourseCompetency competency, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldCreateValidCompetency(CourseCompetency newCompetency) throws Exception {
        newCompetency.setTitle("FreshlyCreatedCompetency");
        newCompetency.setDescription("This is an example of a freshly created competency");
        newCompetency.setCourse(course);
        newCompetency.setMasteryThreshold(42);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        newCompetency.setLectureUnits(connectedLectureUnits);

        CourseCompetency result = createCall(course.getId(), newCompetency, HttpStatus.CREATED);

        assertThat(result.getId()).isNotNull();
        verify(competencyProgressService).updateProgressByCompetencyAndUsersInCourseAsync(eq(result));
    }

    // Test
    void forCompetencyWithNoTitleForCreate(CourseCompetency competency) throws Exception {
        createCall(course.getId(), competency, HttpStatus.BAD_REQUEST);
    }

    // Test
    void forCompetencyWithEmptyTitleForCreate(CourseCompetency competency) throws Exception {
        competency.setTitle(" "); // empty title
        createCall(course.getId(), competency, HttpStatus.BAD_REQUEST);
    }

    // Test
    void forCompetencyWithIdForCreate(CourseCompetency competency) throws Exception {
        competency.setTitle("Hello");
        competency.setId(5L); // id is set
        createCall(course.getId(), competency, HttpStatus.BAD_REQUEST);
    }

    // Test
    void shouldReturnForbiddenForInstructorOfOtherCourseForCreate(CourseCompetency newCompetency) throws Exception {
        newCompetency.setTitle("Example Title");
        createCall(course.getId(), newCompetency, HttpStatus.FORBIDDEN);
    }

    abstract CourseCompetency importCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldImportCompetency() throws Exception {
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(courseCompetency.getId()), Optional.empty(), false, false, false, Optional.empty(), false);
        CourseCompetency importedCompetency = importCall(course2.getId(), importOptions, HttpStatus.CREATED);

        assertThat(courseCompetencyRepository.findById(importedCompetency.getId())).isNotEmpty();
        assertThat(importedCompetency.getTitle()).isEqualTo(courseCompetency.getTitle());
        assertThat(importedCompetency.getDescription()).isEqualTo(courseCompetency.getDescription());
        assertThat(importedCompetency.getMasteryThreshold()).isEqualTo(courseCompetency.getMasteryThreshold());
        assertThat(importedCompetency.getTaxonomy()).isEqualTo(courseCompetency.getTaxonomy());
        assertThat(importedCompetency.getExercises()).isEmpty();
        assertThat(importedCompetency.getLectureUnits()).isEmpty();
        assertThat(importedCompetency.getUserProgress()).isEmpty();
        verify(competencyProgressService, never()).updateProgressByCompetencyAsync(importedCompetency);
    }

    // Test
    void shouldImportExerciseAndLectureWithCompetency() throws Exception {
        ZonedDateTime releaseDate = ZonedDateTime.of(2022, 2, 21, 23, 45, 0, 0, ZoneId.of("UTC"));
        textExercise.setReleaseDate(releaseDate);
        exerciseRepository.save(textExercise);

        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(courseCompetency.getId()), Optional.empty(), false, true, true, Optional.empty(), false);
        importCall(course2.getId(), importOptions, HttpStatus.CREATED);

        course2 = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(course2.getId());
        assertThat(course2.getExercises()).hasSize(2);
        assertThat(course2.getLectures()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits()).hasSize(2);
    }

    // Test
    void shouldImportExerciseAndLectureWithCompetencyAndChangeDates() throws Exception {
        teamTextExercise.setCompetencies(null);
        exerciseRepository.save(teamTextExercise);
        attachmentUnitOfLectureOne.setCompetencies(null);
        attachmentUnitRepository.save(attachmentUnitOfLectureOne);

        ZonedDateTime releaseDate = ZonedDateTime.of(2022, 2, 21, 23, 45, 0, 0, ZoneId.of("UTC"));
        textExercise.setReleaseDate(releaseDate);
        exerciseRepository.save(textExercise);

        ZonedDateTime visibleDate = ZonedDateTime.of(2022, 7, 10, 14, 0, 0, 0, ZoneId.of("UTC"));
        long visibleDateDiff = visibleDate.toEpochSecond() - releaseDate.toEpochSecond();
        lecture.setVisibleDate(visibleDate);
        lectureRepository.save(lecture);

        ZonedDateTime releaseDateTextUnit = ZonedDateTime.of(2022, 7, 10, 20, 0, 0, 0, ZoneId.of("UTC"));
        long releaseDateDiff = releaseDateTextUnit.toEpochSecond() - releaseDate.toEpochSecond();
        textUnitOfLectureOne.setReleaseDate(releaseDateTextUnit);
        textUnitRepository.save(textUnitOfLectureOne);

        ZonedDateTime newReleaseDate = ZonedDateTime.of(2024, 7, 14, 8, 0, 0, 0, ZoneId.of("UTC"));
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(courseCompetency.getId()), Optional.empty(), false, true, true,
                Optional.of(newReleaseDate), true);
        importCall(course2.getId(), importOptions, HttpStatus.CREATED);

        course2 = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(course2.getId());
        assertThat(course2.getExercises()).hasSize(1);
        assertThat(course2.getExercises().stream().findFirst().get().getReleaseDate()).isCloseTo(newReleaseDate, HalfSecond());
        assertThat(course2.getLectures()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getVisibleDate()).isCloseTo(newReleaseDate.plusSeconds(visibleDateDiff), HalfSecond());
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits().stream().findFirst().get().getReleaseDate())
                .isCloseTo(newReleaseDate.plusSeconds(releaseDateDiff), HalfSecond());
    }

    // Test
    void shouldReturnForbiddenForInstructorOfOtherCourseForImport() throws Exception {
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(42L), Optional.empty(), false, false, false, Optional.empty(), false);

        importCall(course.getId(), importOptions, HttpStatus.FORBIDDEN);
    }

    abstract List<? extends CourseCompetency> createBulkCall(long courseId, List<? extends CourseCompetency> competencies, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldCreateCompetencies(CourseCompetency competency1, CourseCompetency competency2) throws Exception {
        competency1.setTitle("Competency1");
        competency1.setDescription("This is an example competency");
        competency1.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
        competency1.setCourse(course);
        competency1.setMasteryThreshold(42);
        competency2.setTitle("Competency2");
        competency2.setDescription("This is another example competency");
        competency2.setTaxonomy(CompetencyTaxonomy.REMEMBER);
        competency2.setCourse(course);
        competency2.setMasteryThreshold(84);

        var competenciesToCreate = List.of(competency1, competency2);

        var persistedCompetencies = createBulkCall(course.getId(), competenciesToCreate, HttpStatus.CREATED);
        assertThat(persistedCompetencies).usingRecursiveFieldByFieldElementComparatorOnFields("title", "description", "taxonomy").isEqualTo(competenciesToCreate);
        assertThat(persistedCompetencies).extracting("id").isNotNull();
    }

    // Test
    void forCompetencyWithNoTitle(CourseCompetency competency) throws Exception {
        createBulkCall(course.getId(), List.of(competency), HttpStatus.BAD_REQUEST);
    }

    // Test
    void forCompetencyWithEmptyTitle(CourseCompetency competency) throws Exception {
        competency.setTitle(" "); // empty title
        createBulkCall(course.getId(), List.of(competency), HttpStatus.BAD_REQUEST);
    }

    // Test
    void forCompetencyWithId(CourseCompetency competency) throws Exception {
        competency.setTitle("Title");
        competency.setId(1L); // id is set
        createBulkCall(course.getId(), List.of(competency), HttpStatus.BAD_REQUEST);
    }

    // Test
    void shouldReturnForbiddenForInstructorOfOtherCourseForCreateBulk() throws Exception {
        createBulkCall(course.getId(), List.of(), HttpStatus.FORBIDDEN);
    }

    abstract List<CompetencyWithTailRelationDTO> importAllCall(long sourceCourseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldImportAllCompetencies(Function<Course, CourseCompetency> createCourseCompetencyForCourse) throws Exception {
        var course3 = courseUtilService.createCourse();

        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course3.getId()), false, false, false, Optional.empty(), false);
        var competencyDTOList = importAllCall(course.getId(), importOptions, HttpStatus.CREATED);

        assertThat(competencyDTOList).isEmpty();

        CourseCompetency head = createCourseCompetencyForCourse.apply(course3);
        CourseCompetency tail = createCourseCompetencyForCourse.apply(course3);
        createRelation(tail, head, RelationType.ASSUMES);

        importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course3.getId()), true, false, false, Optional.empty(), false);
        competencyDTOList = importAllCall(course.getId(), importOptions, HttpStatus.CREATED);

        assertThat(competencyDTOList).hasSize(2);
        // assert that only one of the DTOs has the relation connected
        if (competencyDTOList.getFirst().tailRelations() == null) {
            assertThat(competencyDTOList.get(1).tailRelations()).hasSize(1);
        }
        else {
            assertThat(competencyDTOList.get(1).tailRelations()).isNull();
        }

        importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course3.getId()), false, false, false, Optional.empty(), false);
        competencyDTOList = importAllCall(course.getId(), importOptions, HttpStatus.CREATED);
        assertThat(competencyDTOList).hasSize(2);
        // relations should be empty when not importing them
        assertThat(competencyDTOList.getFirst().tailRelations()).isNull();
        assertThat(competencyDTOList.get(1).tailRelations()).isNull();
    }

    // Test
    void shouldImportAllExerciseAndLectureWithCompetency() throws Exception {
        createProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now());

        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course.getId()), false, true, true, Optional.empty(), false);
        importAllCall(course2.getId(), importOptions, HttpStatus.CREATED);

        course2 = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(course2.getId());
        assertThat(course2.getExercises()).hasSize(2);
        assertThat(course2.getLectures()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits()).hasSize(2);
    }

    // Test
    void shouldImportAllExerciseAndLectureWithCompetencyAndChangeDates() throws Exception {
        teamTextExercise.setCompetencies(null);
        exerciseRepository.save(teamTextExercise);
        attachmentUnitOfLectureOne.setCompetencies(null);
        attachmentUnitRepository.save(attachmentUnitOfLectureOne);

        ZonedDateTime releaseDate = ZonedDateTime.of(2022, 2, 21, 23, 45, 0, 0, ZoneId.of("UTC"));
        textExercise.setReleaseDate(releaseDate);
        exerciseRepository.save(textExercise);

        ZonedDateTime visibleDate = ZonedDateTime.of(2022, 7, 10, 14, 0, 0, 0, ZoneId.of("UTC"));
        long visibleDateDiff = visibleDate.toEpochSecond() - releaseDate.toEpochSecond();
        lecture.setVisibleDate(visibleDate);
        lectureRepository.save(lecture);

        ZonedDateTime releaseDateTextUnit = ZonedDateTime.of(2022, 7, 10, 20, 0, 0, 0, ZoneId.of("UTC"));
        long releaseDateDiff = releaseDateTextUnit.toEpochSecond() - releaseDate.toEpochSecond();
        textUnitOfLectureOne.setReleaseDate(releaseDateTextUnit);
        textUnitRepository.save(textUnitOfLectureOne);

        ZonedDateTime newReleaseDate = ZonedDateTime.of(2024, 7, 14, 8, 0, 0, 0, ZoneId.of("UTC"));
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course.getId()), false, true, true, Optional.of(newReleaseDate), true);
        importAllCall(course2.getId(), importOptions, HttpStatus.CREATED);

        course2 = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(course2.getId());
        assertThat(course2.getExercises()).hasSize(1);
        assertThat(course2.getExercises().stream().findFirst().get().getReleaseDate()).isCloseTo(newReleaseDate, HalfSecond());
        assertThat(course2.getLectures()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getVisibleDate()).isCloseTo(newReleaseDate.plusSeconds(visibleDateDiff), HalfSecond());
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits().stream().findFirst().get().getReleaseDate())
                .isCloseTo(newReleaseDate.plusSeconds(releaseDateDiff), HalfSecond());
    }

    // Test
    void shouldReturnForbiddenForInstructorNotInCourse() throws Exception {
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course2.getId()), false, false, false, Optional.empty(), false);
        importAllCall(course.getId(), importOptions, HttpStatus.FORBIDDEN);
    }

    // Test
    void shouldReturnBadRequestForImportFromSameCourse() throws Exception {
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.of(course.getId()), false, false, false, Optional.empty(), false);
        importAllCall(course.getId(), importOptions, HttpStatus.BAD_REQUEST);
    }

    abstract List<CompetencyImportResponseDTO> importStandardizedCall(long courseId, List<Long> idList, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldImportStandardizedCompetencies() throws Exception {
        var knowledgeArea = standardizedCompetencyUtilService.saveKnowledgeArea("KnowledgeArea 1", "KA1", "", null);
        var competency1 = standardizedCompetencyUtilService.saveStandardizedCompetency("Competency1", "description 1", CompetencyTaxonomy.ANALYZE, "1.3.1", knowledgeArea, null);
        var competency2 = standardizedCompetencyUtilService.saveStandardizedCompetency("Competency2", "description 2", CompetencyTaxonomy.CREATE, "1.0.0", knowledgeArea, null);

        var idList = List.of(competency1.getId(), competency2.getId());

        var actualCompetencies = importStandardizedCall(course.getId(), idList, HttpStatus.CREATED);

        assertThat(actualCompetencies).hasSize(2);
        var actualCompetency1 = actualCompetencies.getFirst();
        var actualCompetency2 = actualCompetencies.get(1);
        if (!competency1.getId().equals(actualCompetency1.linkedStandardizedCompetencyId())) {
            var tempCompetency = actualCompetency1;
            actualCompetency1 = actualCompetency2;
            actualCompetency2 = tempCompetency;
        }

        assertThat(actualCompetency1).usingRecursiveComparison().comparingOnlyFields("title", "description", "taxonomy").isEqualTo(competency1);
        assertThat(actualCompetency1.linkedStandardizedCompetencyId()).isEqualTo(competency1.getId());
        assertThat(actualCompetency2).usingRecursiveComparison().comparingOnlyFields("title", "description", "taxonomy").isEqualTo(competency2);
        assertThat(actualCompetency2.linkedStandardizedCompetencyId()).isEqualTo(competency2.getId());
    }

    // Test
    void shouldReturnNotFoundForNotExistingIds() throws Exception {
        List<Long> idList = List.of(-1000L, -1001L);
        importStandardizedCall(course.getId(), idList, HttpStatus.NOT_FOUND);
    }

    abstract List<CompetencyWithTailRelationDTO> importBulkCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception;

    // Test
    void shouldReturnForbiddenForInstructorOfOtherCourseForBulkImport() throws Exception {
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(), Optional.empty(), false, false, false, Optional.empty(), false);
        importBulkCall(course.getId(), importOptions, HttpStatus.FORBIDDEN);
    }

    // Test
    void shouldImportCompetencies(Function<Course, CourseCompetency> createCourseCompetencyForCourse) throws Exception {
        CourseCompetency head = createCourseCompetencyForCourse.apply(course2);
        CourseCompetency tail = createCourseCompetencyForCourse.apply(course2);
        createRelation(tail, head, RelationType.ASSUMES);
        Set<Long> competencyIds = Set.of(head.getId(), tail.getId());

        var importOptions = new CompetencyImportOptionsDTO(competencyIds, Optional.empty(), true, false, false, Optional.empty(), false);
        var competencyDTOList = importBulkCall(course.getId(), importOptions, HttpStatus.CREATED);

        assertThat(competencyDTOList).hasSize(2);
        // competency 2 should be the tail of one relation
        if (competencyDTOList.getFirst().tailRelations() != null) {
            assertThat(competencyDTOList.getFirst().tailRelations()).hasSize(1);
            assertThat(competencyDTOList.get(1).tailRelations()).isNull();
        }
        else {
            assertThat(competencyDTOList.getFirst().tailRelations()).isNull();
            assertThat(competencyDTOList.get(1).tailRelations()).hasSize(1);
        }

        importOptions = new CompetencyImportOptionsDTO(competencyIds, Optional.empty(), false, false, false, Optional.empty(), false);
        competencyDTOList = importBulkCall(course.getId(), importOptions, HttpStatus.CREATED);
        assertThat(competencyDTOList).hasSize(2);
        // relations should be empty when not importing them
        assertThat(competencyDTOList.getFirst().tailRelations()).isNull();
        assertThat(competencyDTOList.get(1).tailRelations()).isNull();
    }

    // Test
    void shouldImportCompetenciesExerciseAndLectureWithCompetency() throws Exception {
        ZonedDateTime releaseDate = ZonedDateTime.of(2022, 2, 21, 23, 45, 0, 0, ZoneId.of("UTC"));
        textExercise.setReleaseDate(releaseDate);
        exerciseRepository.save(textExercise);

        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(courseCompetency.getId()), Optional.empty(), false, true, true, Optional.empty(), false);
        importBulkCall(course2.getId(), importOptions, HttpStatus.CREATED);

        course2 = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(course2.getId());
        assertThat(course2.getExercises()).hasSize(2);
        assertThat(course2.getLectures()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits()).hasSize(2);
    }

    // Test
    void shouldImportCompetenciesExerciseAndLectureWithCompetencyAndChangeDates() throws Exception {
        teamTextExercise.setCompetencies(null);
        exerciseRepository.save(teamTextExercise);
        attachmentUnitOfLectureOne.setCompetencies(null);
        attachmentUnitRepository.save(attachmentUnitOfLectureOne);

        ZonedDateTime releaseDate = ZonedDateTime.of(2022, 2, 21, 23, 45, 0, 0, ZoneId.of("UTC"));
        textExercise.setReleaseDate(releaseDate);
        exerciseRepository.save(textExercise);

        ZonedDateTime visibleDate = ZonedDateTime.of(2022, 7, 10, 14, 0, 0, 0, ZoneId.of("UTC"));
        long visibleDateDiff = visibleDate.toEpochSecond() - releaseDate.toEpochSecond();
        lecture.setVisibleDate(visibleDate);
        lectureRepository.save(lecture);

        ZonedDateTime releaseDateTextUnit = ZonedDateTime.of(2022, 7, 10, 20, 0, 0, 0, ZoneId.of("UTC"));
        long releaseDateDiff = releaseDateTextUnit.toEpochSecond() - releaseDate.toEpochSecond();
        textUnitOfLectureOne.setReleaseDate(releaseDateTextUnit);
        textUnitRepository.save(textUnitOfLectureOne);

        ZonedDateTime newReleaseDate = ZonedDateTime.of(2024, 7, 14, 8, 0, 0, 0, ZoneId.of("UTC"));
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(courseCompetency.getId()), Optional.empty(), false, true, true,
                Optional.of(newReleaseDate), true);
        importBulkCall(course2.getId(), importOptions, HttpStatus.CREATED);

        course2 = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndCompetenciesElseThrow(course2.getId());
        assertThat(course2.getExercises()).hasSize(1);
        assertThat(course2.getExercises().stream().findFirst().get().getReleaseDate()).isCloseTo(newReleaseDate, HalfSecond());
        assertThat(course2.getLectures()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getVisibleDate()).isCloseTo(newReleaseDate.plusSeconds(visibleDateDiff), HalfSecond());
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits()).hasSize(1);
        assertThat(course2.getLectures().stream().findFirst().get().getLectureUnits().stream().findFirst().get().getReleaseDate())
                .isCloseTo(newReleaseDate.plusSeconds(releaseDateDiff), HalfSecond());
    }
}
