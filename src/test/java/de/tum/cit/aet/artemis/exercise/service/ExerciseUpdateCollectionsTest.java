package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;

class ExerciseUpdateCollectionsTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void leavesMissingCollectionsAbsent(boolean text) {
        Exercise exercise = exercise(text);
        exercise.setGradingCriteria(null);
        exercise.setCompetencyLinks(null);

        CollectionsAndOwner mapped = map(exercise);

        assertThat(mapped.criteria()).isNull();
        assertThat(mapped.links()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void leavesDetachedLazyCollectionsUninitialized(boolean text) {
        Exercise exercise = exercise(text);
        var criteria = new PersistentSet<GradingCriterion>();
        var links = new PersistentSet<CompetencyExerciseLink>();
        // Reproduce Hibernate field hydration without the entity setter, which reconnects loaded rubric rows.
        ReflectionTestUtils.setField(exercise, "gradingCriteria", criteria);
        ReflectionTestUtils.setField(exercise, "competencyLinks", links);

        CollectionsAndOwner mapped = map(exercise);

        assertThat(mapped.criteria()).isNull();
        assertThat(mapped.links()).isNull();
        assertThat(Hibernate.isInitialized(criteria)).isFalse();
        assertThat(Hibernate.isInitialized(links)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void preservesInitializedEmptyCollections(boolean text) {
        Exercise exercise = exercise(text);
        exercise.setGradingCriteria(Set.of());
        exercise.setCompetencyLinks(Set.of());

        CollectionsAndOwner mapped = map(exercise);

        assertThat(mapped.criteria()).isEmpty();
        assertThat(mapped.links()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void mapsInitializedCriteriaInOrderAndCompetencyLinkMetadata(boolean text) {
        Exercise exercise = exercise(text);
        GradingCriterion first = new GradingCriterion();
        first.setId(4L);
        first.setSortOrder(0);
        first.setTitle("First");
        GradingCriterion second = new GradingCriterion();
        second.setId(3L);
        second.setSortOrder(1);
        second.setTitle("Second");
        exercise.setGradingCriteria(Set.of(second, first));
        Competency competency = new Competency();
        competency.setId(8L);
        competency.setTitle("Apply knowledge");
        CompetencyExerciseLink link = new CompetencyExerciseLink(competency, exercise, 0.75);
        link.setGeneratedByAi(true);
        exercise.setCompetencyLinks(Set.of(link));

        CollectionsAndOwner mapped = map(exercise);

        assertThat(mapped.criteria()).extracting(GradingCriterionDTO::title).containsExactly("First", "Second");
        assertThat(mapped.links()).singleElement().satisfies(dto -> {
            assertThat(dto.competency().id()).isEqualTo(8L);
            assertThat(dto.competency().title()).isEqualTo("Apply knowledge");
            assertThat(dto.weight()).isEqualTo(0.75);
            assertThat(dto.generatedByAi()).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void retainsTypeSpecificExamCourseId(boolean text) {
        Exercise exercise = exercise(text);
        Course course = new Course();
        course.setId(12L);
        Exam exam = new Exam();
        exam.setCourse(course);
        ExerciseGroup group = new ExerciseGroup();
        group.setId(15L);
        group.setExam(exam);
        exercise.setExerciseGroup(group);

        CollectionsAndOwner mapped = map(exercise);

        assertThat(mapped.courseId()).isEqualTo(text ? null : 12L);
        assertThat(mapped.groupId()).isEqualTo(15L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void retainsDirectCourseId(boolean text) {
        Exercise exercise = exercise(text);
        Course course = new Course();
        course.setId(12L);
        exercise.setCourse(course);

        CollectionsAndOwner mapped = map(exercise);

        assertThat(mapped.courseId()).isEqualTo(12L);
        assertThat(mapped.groupId()).isNull();
    }

    private Exercise exercise(boolean text) {
        Exercise exercise = text ? new TextExercise() : new FileUploadExercise();
        exercise.setId(1L);
        return exercise;
    }

    private CollectionsAndOwner map(Exercise exercise) {
        if (exercise instanceof TextExercise text) {
            var dto = UpdateTextExerciseDTO.of(text);
            return new CollectionsAndOwner(dto.gradingCriteria(), dto.competencyLinks(), dto.courseId(), dto.exerciseGroupId());
        }
        var dto = UpdateFileUploadExerciseDTO.of((FileUploadExercise) exercise);
        return new CollectionsAndOwner(dto.gradingCriteria(), dto.competencyLinks(), dto.courseId(), dto.exerciseGroupId());
    }

    private record CollectionsAndOwner(List<GradingCriterionDTO> criteria, Set<CompetencyLinkDTO> links, Long courseId, Long groupId) {
    }
}
