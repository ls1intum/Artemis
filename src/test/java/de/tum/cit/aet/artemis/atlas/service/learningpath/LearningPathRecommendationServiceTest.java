package de.tum.cit.aet.artemis.atlas.service.learningpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.DoubleStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO.LearningObjectType;
import de.tum.cit.aet.artemis.atlas.service.LearningObjectService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRecommendationService.RecommendationState;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyProgressTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class LearningPathRecommendationServiceTest {

    private LearningObjectService objects;

    private ParticipantScoreService scores;

    private CompetencyProgressTestRepository progress;

    private LearningPathRecommendationService service;

    private de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository relations;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        objects = mock(LearningObjectService.class);
        scores = mock(ParticipantScoreService.class);
        progress = mock(CompetencyProgressTestRepository.class);
        relations = mock(de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository.class);
        service = new LearningPathRecommendationService(relations, objects, scores, progress, null, null);
        user.setId(1L);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 3, 4, 5 })
    void gradePreferencesNeverRecommendCompletedExercisesAndKeepDifficultyOrdering(int aim) {
        var competency = competency(2L);
        competency.setMasteryThreshold(1);
        var easy = exercise(10L, DifficultyLevel.EASY, IncludedInOverallScore.INCLUDED_COMPLETELY);
        var medium = exercise(11L, null, IncludedInOverallScore.INCLUDED_AS_BONUS);
        var hard = exercise(12L, DifficultyLevel.HARD, IncludedInOverallScore.NOT_INCLUDED);
        var completed = exercise(13L, DifficultyLevel.EASY, IncludedInOverallScore.INCLUDED_COMPLETELY);
        competency.setExerciseLinks(Set.of(new CompetencyExerciseLink(competency, easy, 1.0), new CompetencyExerciseLink(competency, medium, 0.5),
                new CompetencyExerciseLink(competency, hard, 0.3), new CompetencyExerciseLink(competency, completed, 1.0)));
        when(objects.isCompletedByUser(completed, user)).thenReturn(true);
        when(scores.getStudentAndTeamParticipationPointsAsDoubleStream(any(), any())).thenAnswer(call -> DoubleStream.of(0));
        var profile = new CourseLearnerProfile();
        profile.setAimForGradeOrBonus(aim);
        var result = service.getRecommendedOrderOfLearningObjects(user, profile, competency, 1.0, true);
        assertThat(result).doesNotContain(completed).contains(easy, hard);
        assertThat(result.indexOf(easy)).isLessThan(result.indexOf(hard));
        if (aim == 5) {
            assertThat(result).containsExactly(easy, medium, hard);
        }
        else {
            assertThat(result).containsExactly(easy, hard);
        }
    }

    @Test
    void repetitionSelectsOnlyOldProgressAndOrdersOldestFirst() {
        var missing = competency(1L);
        var recent = competency(2L);
        var old = competency(3L);
        var oldest = competency(4L);
        recent.setUserProgress(Set.of(progressAt(Instant.now().minusSeconds(60))));
        old.setUserProgress(Set.of(progressAt(Instant.now().minusSeconds(20 * 86400))));
        oldest.setUserProgress(Set.of(progressAt(Instant.now().minusSeconds(30 * 86400))));
        old.setSoftDueDate(ZonedDateTime.now().minusDays(14));
        var state = state(Map.of(1L, missing, 2L, recent, 3L, old, 4L, oldest), List.of(1L), Map.of(1L, 0d));
        var profile = new CourseLearnerProfile();
        profile.setTimeInvestment(2);
        assertThat(service.determineCompetenciesForRepeatedTests(state, profile)).containsExactly(oldest, old);
    }

    @Test
    void navigationSkipsObjectsBeforeCurrentAndReturnsNullAtEnd() {
        var competency = competency(2L);
        competency.setMasteryThreshold(100);
        var first = exercise(10L, DifficultyLevel.EASY, IncludedInOverallScore.INCLUDED_COMPLETELY);
        var second = exercise(11L, DifficultyLevel.MEDIUM, IncludedInOverallScore.INCLUDED_COMPLETELY);
        competency.setExerciseLinks(Set.of(new CompetencyExerciseLink(competency, first, 1.0), new CompetencyExerciseLink(competency, second, 0.5)));
        when(scores.getStudentAndTeamParticipationPointsAsDoubleStream(any(), any())).thenAnswer(call -> DoubleStream.of(0));
        var state = state(Map.of(2L, competency), List.of(2L), Map.of(2L, 10d));
        var profile = new CourseLearnerProfile();
        var result = service.findLearningObject(user, profile, state, List.of(),
                new LearningPathNavigationObjectDTO(10L, false, "first", 2L, LearningObjectType.EXERCISE, false, false));
        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.competencyId()).isEqualTo(2L);
        assertThat(result.completed()).isFalse();
        assertThat(service.findLearningObject(user, profile, state, List.of(), result)).isNull();
        assertThat(service.findLearningObject(user, profile, state, List.of(),
                new LearningPathNavigationObjectDTO(999L, false, "missing", 2L, LearningObjectType.EXERCISE, false, false))).isNull();
    }

    @Test
    void emptyFinishedPathOnlyOffersRepetitionAndFallsBackToPendingCourseWork() {
        var competency = competency(2L);
        var empty = state(Map.of(2L, competency), java.util.Arrays.asList((Long) null), Map.of(2L, 0d));
        assertThat(service.findLearningObject(user, new CourseLearnerProfile(), empty, List.of(competency), null)).isNull();
        var pending = state(Map.of(2L, competency), List.of(2L), Map.of(2L, 0d));
        assertThat(service.findLearningObject(user, new CourseLearnerProfile(), pending, List.of(competency), null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "second", "first", "none", "repeat" })
    void previousNavigationRespectsObjectPositionAndRepetitionBoundary(String position) {
        var competency = competency(2L);
        competency.setMasteryThreshold(100);
        var first = exercise(10L, DifficultyLevel.EASY, IncludedInOverallScore.INCLUDED_COMPLETELY);
        var second = exercise(11L, DifficultyLevel.MEDIUM, IncludedInOverallScore.INCLUDED_COMPLETELY);
        competency.setExerciseLinks(Set.of(new CompetencyExerciseLink(competency, first, 1.0), new CompetencyExerciseLink(competency, second, 0.5)));
        when(scores.getStudentAndTeamParticipationPointsAsDoubleStream(any(), any())).thenAnswer(call -> DoubleStream.of(0));
        when(relations.getMatchingCompetenciesByCompetencyId(2L)).thenReturn(Set.of(2L));
        var path = new de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath();
        path.setUser(user);
        path.setCompetencies(Set.of(competency));
        var state = state(Map.of(2L, competency), List.of(2L), Map.of(2L, 100d));
        LearningPathNavigationObjectDTO current = position.equals("none") ? null
                : new LearningPathNavigationObjectDTO(position.equals("second") ? 11L : 10L, true, "Current", 2L, LearningObjectType.EXERCISE, position.equals("repeat"), false);
        var previous = service.findPreviousLearningObject(path, new CourseLearnerProfile(), state, current);
        if (position.equals("first")) {
            assertThat(previous).isNull();
        }
        else {
            assertThat(previous.id()).isEqualTo(position.equals("second") ? 10L : 11L);
            assertThat(previous.repeatedTest()).isFalse();
            assertThat(previous.competencyId()).isEqualTo(2L);
        }
    }

    @Test
    void recommendationPrefersOverdueCompetenciesAndCollapsesMatchingCluster() {
        var overdue = competency(1L);
        overdue.setMasteryThreshold(100);
        overdue.setSoftDueDate(ZonedDateTime.now().minusDays(20));
        var future = competency(2L);
        future.setMasteryThreshold(100);
        future.setSoftDueDate(ZonedDateTime.now().plusDays(20));
        var match = competency(3L);
        match.setMasteryThreshold(100);
        when(relations.getMatchingCompetenciesByCompetencyId(1L)).thenReturn(Set.of(1L));
        when(relations.getMatchingCompetenciesByCompetencyId(2L)).thenReturn(Set.of(2L, 3L));
        when(relations.getMatchingCompetenciesByCompetencyId(3L)).thenReturn(Set.of(2L, 3L));
        var path = new de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath();
        path.setCompetencies(new java.util.LinkedHashSet<>(List.of(overdue, future, match)));
        var result = service.getRecommendedOrderOfNotMasteredCompetencies(path);
        assertThat(result.recommendedOrderOfCompetencies().getFirst()).isEqualTo(1L);
        assertThat(result.recommendedOrderOfCompetencies()).hasSize(2);
        assertThat(result.recommendedOrderOfCompetencies()).containsAnyOf(2L, 3L);
        org.mockito.Mockito.verify(relations).getMatchingCompetenciesByCompetencyId(2L);
        org.mockito.Mockito.verify(relations, org.mockito.Mockito.never()).getMatchingCompetenciesByCompetencyId(3L);
    }

    private Competency competency(long id) {
        var competency = new Competency();
        competency.setId(id);
        return competency;
    }

    private ProgrammingExercise exercise(long id, DifficultyLevel difficulty, IncludedInOverallScore included) {
        var exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setTitle("Exercise " + id);
        exercise.setMaxPoints(10.0);
        exercise.setDifficulty(difficulty);
        exercise.setIncludedInOverallScore(included);
        return exercise;
    }

    private CompetencyProgress progressAt(Instant modified) {
        var progress = mock(CompetencyProgress.class);
        when(progress.getLastModifiedDate()).thenReturn(modified);
        return progress;
    }

    private RecommendationState state(Map<Long, CourseCompetency> competencies, List<Long> order, Map<Long, Double> mastery) {
        var prior = new java.util.HashMap<Long, Set<Long>>();
        competencies.keySet().forEach(id -> prior.put(id, Set.of()));
        return new RecommendationState(competencies, order, Set.of(), mastery, Map.of(), prior, Map.of(), Map.of());
    }
}
