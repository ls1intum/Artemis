package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyWithTailRelation;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyLectureUnitLinkTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureImportApi;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class LearningObjectImportServiceTest {

    private ExerciseTestRepository exercises;

    private ProgrammingExerciseTestRepository programming;

    private CourseCompetencyRepository competencies;

    private CompetencyExerciseLinkTestRepository exerciseLinks;

    private CompetencyLectureUnitLinkTestRepository unitLinks;

    private LectureRepositoryApi lectures;

    private LectureUnitRepositoryApi units;

    private LectureUnitApi unitApi;

    private LectureImportApi lectureImport;

    private LearningObjectImportService service;

    private Course destination;

    @BeforeEach
    void setUp() {
        exercises = mock(ExerciseTestRepository.class);
        programming = mock(ProgrammingExerciseTestRepository.class);
        competencies = mock(CourseCompetencyRepository.class);
        exerciseLinks = mock(CompetencyExerciseLinkTestRepository.class);
        unitLinks = mock(CompetencyLectureUnitLinkTestRepository.class);
        lectures = mock(LectureRepositoryApi.class);
        units = mock(LectureUnitRepositoryApi.class);
        unitApi = mock(LectureUnitApi.class);
        lectureImport = mock(LectureImportApi.class);
        service = new LearningObjectImportService(exercises, programming, null, Optional.empty(), Optional.empty(), Optional.empty(), null, null, Optional.of(lectures),
                Optional.of(units), Optional.of(unitApi), Optional.of(lectureImport), competencies, null, null, exerciseLinks, unitLinks, null);
        destination = new Course();
        destination.setId(2L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void reusedProgrammingExerciseShiftsAllDatesAndPreservesLinkProvenance(boolean useReleaseDate) {
        var source = competency(1L);
        var imported = competency(2L);
        var exercise = new ProgrammingExercise();
        exercise.setId(3L);
        exercise.setTitle("Existing");
        exercise.setShortName("existing");
        var original = ZonedDateTime.parse("2026-01-01T10:00:00Z");
        exercise.setReleaseDate(original);
        exercise.setDueDate(original.plusDays(1));
        exercise.setStartDate(original.plusHours(1));
        exercise.setAssessmentDueDate(original.plusDays(2));
        exercise.setExampleSolutionPublicationDate(original.plusDays(3));
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(original.plusDays(4));
        imported.setSoftDueDate(original.plusDays(5));
        var sourceLink = new CompetencyExerciseLink(source, exercise, 0.5);
        sourceLink.setGeneratedByAi(true);
        source.getExerciseLinks().add(sourceLink);
        when(programming.findWithCompetenciesByTitleAndCourseId("Existing", 2L)).thenReturn(Optional.of(exercise));
        when(programming.findByShortNameAndCourseIdWithCompetencies("existing", 2L)).thenReturn(Optional.of(exercise));
        when(exerciseLinks.save(any())).thenAnswer(call -> call.getArgument(0));
        var reference = useReleaseDate ? original.plusDays(10) : original.plusDays(11);
        service.importRelatedLearningObjects(List.of(source), Map.of(1L, new CompetencyWithTailRelation(imported, List.of())), destination,
                new CompetencyImportOptionsDTO(Set.of(1L), Optional.empty(), false, true, false, Optional.of(reference), useReleaseDate));
        assertThat(exercise.getReleaseDate()).isEqualTo(original.plusDays(10));
        assertThat(exercise.getDueDate()).isEqualTo(original.plusDays(11));
        assertThat(exercise.getStartDate()).isEqualTo(original.plusDays(10).plusHours(1));
        assertThat(exercise.getAssessmentDueDate()).isEqualTo(original.plusDays(12));
        assertThat(exercise.getExampleSolutionPublicationDate()).isEqualTo(original.plusDays(13));
        assertThat(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()).isEqualTo(original.plusDays(14));
        assertThat(imported.getSoftDueDate()).isEqualTo(original.plusDays(15));
        assertThat(imported.getExerciseLinks()).singleElement().satisfies(link -> {
            assertThat(link.getExercise()).isSameAs(exercise);
            assertThat(link.getWeight()).isEqualTo(0.5);
            assertThat(link.isGeneratedByAi()).isTrue();
        });
        verify(exercises).saveAll(Set.of(exercise));
    }

    @Test
    void programmingTitleCollisionSkipsFailedExerciseAndStillPersistsCompetency() {
        var source = competency(1L);
        var imported = competency(2L);
        var first = new ProgrammingExercise();
        first.setId(3L);
        first.setTitle("Collision");
        first.setShortName("collision");
        var second = new ProgrammingExercise();
        second.setId(4L);
        source.getExerciseLinks().add(new CompetencyExerciseLink(source, first, 1.0));
        when(programming.findWithCompetenciesByTitleAndCourseId("Collision", 2L)).thenReturn(Optional.of(first));
        when(programming.findByShortNameAndCourseIdWithCompetencies("collision", 2L)).thenReturn(Optional.of(second));
        service.importRelatedLearningObjects(List.of(source), Map.of(1L, new CompetencyWithTailRelation(imported, List.of())), destination,
                new CompetencyImportOptionsDTO(Set.of(1L), Optional.empty(), false, true, false, Optional.empty(), false));
        assertThat(imported.getExerciseLinks()).isEmpty();
        verify(competencies).saveAll(Set.of(imported));
        verify(exercises).saveAll(Set.of());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void lectureImportReusesExistingLectureAndHandlesReusedOrNewUnit(boolean reuseUnit) throws de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException {
        var source = competency(1L);
        var imported = competency(2L);
        var sourceLecture = new Lecture();
        sourceLecture.setTitle("Lecture");
        var targetLecture = new Lecture();
        targetLecture.setTitle("Lecture");
        var sourceUnit = new TextUnit();
        sourceUnit.setId(3L);
        sourceUnit.setName("Unit");
        sourceUnit.setLecture(sourceLecture);
        var targetUnit = new TextUnit();
        targetUnit.setId(4L);
        targetUnit.setName("Unit");
        targetUnit.setLecture(targetLecture);
        var sourceLink = new CompetencyLectureUnitLink(source, sourceUnit, 0.3);
        sourceLink.setGeneratedByAi(true);
        source.getLectureUnitLinks().add(sourceLink);
        when(lectures.findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow("Lecture", 2L)).thenReturn(Optional.of(targetLecture));
        when(units.findByNameAndLectureTitleAndCourseIdWithCompetencies("Unit", "Lecture", 2L)).thenReturn(reuseUnit ? Optional.of(targetUnit) : Optional.empty());
        if (!reuseUnit) {
            when(unitApi.importLectureUnit(sourceUnit, targetLecture)).thenReturn(targetUnit);
        }
        when(unitLinks.save(any())).thenAnswer(call -> call.getArgument(0));
        service.importRelatedLearningObjects(List.of(source), Map.of(1L, new CompetencyWithTailRelation(imported, List.of())), destination,
                new CompetencyImportOptionsDTO(Set.of(1L), Optional.empty(), false, false, true, Optional.empty(), false));
        assertThat(imported.getLectureUnitLinks()).singleElement().satisfies(link -> {
            assertThat(link.getLectureUnit()).isSameAs(targetUnit);
            assertThat(link.getWeight()).isEqualTo(0.3);
            assertThat(link.isGeneratedByAi()).isTrue();
        });
        verify(lectures).saveAll(Set.of(targetLecture));
    }

    @Test
    void noDatedLearningObjectsLeavesCompetencyDatesUnchanged() {
        var imported = competency(2L);
        service.importRelatedLearningObjects(List.of(), Map.of(1L, new CompetencyWithTailRelation(imported, List.of())), destination,
                new CompetencyImportOptionsDTO(Set.of(), Optional.empty(), false, false, false, Optional.of(ZonedDateTime.parse("2026-01-01T10:00:00Z")), true));
        assertThat(imported.getSoftDueDate()).isNull();
        verify(competencies).saveAll(Set.of(imported));
    }

    @Test
    void importedLectureAndUnitDatesShiftTogetherAndSharedLectureIsImportedOnce() throws de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException {
        var source = competency(1L);
        var imported = competency(2L);
        var sourceLecture = new Lecture();
        sourceLecture.setTitle("Lecture");
        var targetLecture = new Lecture();
        targetLecture.setTitle("Lecture");
        var original = ZonedDateTime.parse("2026-01-01T10:00:00Z");
        targetLecture.setStartDate(original);
        targetLecture.setEndDate(original.plusDays(1));
        var sourceFirst = new TextUnit();
        sourceFirst.setId(3L);
        sourceFirst.setName("First");
        sourceFirst.setLecture(sourceLecture);
        var sourceSecond = new TextUnit();
        sourceSecond.setId(4L);
        sourceSecond.setName("Second");
        sourceSecond.setLecture(sourceLecture);
        var targetFirst = new TextUnit();
        targetFirst.setId(5L);
        targetFirst.setReleaseDate(original);
        var targetSecond = new TextUnit();
        targetSecond.setId(6L);
        source.getLectureUnitLinks().add(new CompetencyLectureUnitLink(source, sourceFirst, 0.5));
        source.getLectureUnitLinks().add(new CompetencyLectureUnitLink(source, sourceSecond, 1.0));
        when(lectures.findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow("Lecture", 2L)).thenReturn(Optional.empty());
        when(lectureImport.importLecture(sourceLecture, destination, false)).thenReturn(targetLecture);
        when(unitApi.importLectureUnit(sourceFirst, targetLecture)).thenReturn(targetFirst);
        when(unitApi.importLectureUnit(sourceSecond, targetLecture)).thenReturn(targetSecond);
        when(unitLinks.save(any())).thenAnswer(call -> call.getArgument(0));
        service.importRelatedLearningObjects(List.of(source), Map.of(1L, new CompetencyWithTailRelation(imported, List.of())), destination,
                new CompetencyImportOptionsDTO(Set.of(1L), Optional.empty(), false, false, true, Optional.of(original.plusDays(10)), true));
        assertThat(targetLecture.getStartDate()).isEqualTo(original.plusDays(10));
        assertThat(targetLecture.getEndDate()).isEqualTo(original.plusDays(11));
        assertThat(targetFirst.getReleaseDate()).isEqualTo(original.plusDays(10));
        assertThat(targetSecond.getReleaseDate()).isNull();
        assertThat(imported.getLectureUnitLinks()).hasSize(2).allSatisfy(link -> assertThat(link.isGeneratedByAi()).isFalse());
        verify(lectureImport).importLecture(sourceLecture, destination, false);
        verify(lectures).findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow("Lecture", 2L);
    }

    @Test
    void quizReuseShiftsOnlyDatedBatchesAndLeavesMissingExerciseDatesUntouched() throws de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException {
        var quizzes = mock(de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository.class);
        service = new LearningObjectImportService(exercises, programming, null, Optional.empty(), Optional.empty(), Optional.empty(), quizzes, null, Optional.of(lectures),
                Optional.of(units), Optional.of(unitApi), Optional.of(lectureImport), competencies, null, null, exerciseLinks, unitLinks, null);
        var source = competency(1L);
        var imported = competency(2L);
        var quiz = new de.tum.cit.aet.artemis.quiz.domain.QuizExercise();
        quiz.setId(3L);
        quiz.setTitle("Quiz");
        var original = ZonedDateTime.parse("2026-01-01T10:00:00Z");
        quiz.setDueDate(original);
        var dated = new de.tum.cit.aet.artemis.quiz.domain.QuizBatch();
        dated.setId(4L);
        dated.setStartTime(original.minusHours(1));
        var undated = new de.tum.cit.aet.artemis.quiz.domain.QuizBatch();
        undated.setId(5L);
        quiz.setQuizBatches(Set.of(dated, undated));
        source.getExerciseLinks().add(new CompetencyExerciseLink(source, quiz, 1.0));
        when(quizzes.findUniqueWithCompetenciesByTitleAndCourseId("Quiz", 2L)).thenReturn(Optional.of(quiz));
        when(exerciseLinks.save(any())).thenAnswer(call -> call.getArgument(0));
        service.importRelatedLearningObjects(List.of(source), Map.of(1L, new CompetencyWithTailRelation(imported, List.of())), destination,
                new CompetencyImportOptionsDTO(Set.of(1L), Optional.empty(), false, true, false, Optional.of(original.plusDays(10)), false));
        assertThat(quiz.getDueDate()).isEqualTo(original.plusDays(10));
        assertThat(dated.getStartTime()).isEqualTo(original.plusDays(10).minusHours(1));
        assertThat(undated.getStartTime()).isNull();
        assertThat(quiz.getReleaseDate()).isNull();
        assertThat(quiz.getAssessmentDueDate()).isNull();
    }

    private Competency competency(long id) {
        var competency = new Competency();
        competency.setId(id);
        return competency;
    }
}
