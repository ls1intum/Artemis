package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Focused unit test for the backfill contract of {@link ExerciseImportService#copyExerciseBasis}. This is the exact
 * logic that silently dropped content during bulk import (exam / course-material) before it was fixed: the merge rule
 * must keep the caller's value on {@code newExercise} where present and otherwise take it from {@code sourceExercise}.
 */
class ExerciseImportServiceTest {

    /**
     * Minimal concrete subclass exposing the protected copy helpers. {@code copyExerciseBasis} and
     * {@code prepareNewExerciseForImport} do not use any injected dependency, so passing {@code null} is safe here.
     */
    private static final class TestableExerciseImport extends ExerciseImportService {

        private TestableExerciseImport() {
            super(null, null, null, null);
        }

        private void copyBasis(Exercise newExercise, Exercise sourceExercise) {
            copyExerciseBasis(newExercise, sourceExercise, new HashMap<>());
        }
    }

    private final TestableExerciseImport service = new TestableExerciseImport();

    private TextExercise sourceWithContent() {
        Course course = new Course();
        TextExercise source = new TextExercise();
        source.setCourse(course);
        source.setTitle("Source title");
        source.setProblemStatement("Source problem statement");
        source.setDifficulty(DifficultyLevel.HARD);
        source.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        source.setGradingInstructions("Source grading instructions");
        source.setMaxPoints(7.0);
        source.setBonusPoints(1.0);
        GradingCriterion criterion = new GradingCriterion();
        criterion.setTitle("Source criterion");
        source.setGradingCriteria(new HashSet<>(Set.of(criterion)));
        return source;
    }

    @Test
    void backfillsEveryContentFieldFromSourceWhenNewExerciseIsAnEmptySkeleton() {
        TextExercise source = sourceWithContent();
        // A bulk-import skeleton: only the destination is set, everything else is a fresh default. Bulk callers null the
        // grading criteria to request the deep copy from the source (see ExerciseImportService#copyExerciseBasis).
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        newExercise.setGradingCriteria(null);

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getProblemStatement()).isEqualTo("Source problem statement");
        assertThat(newExercise.getDifficulty()).isEqualTo(DifficultyLevel.HARD);
        assertThat(newExercise.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(newExercise.getGradingInstructions()).isEqualTo("Source grading instructions");
        // Grading criteria must be deep-copied from the source, not left as the skeleton's empty set (regression guard).
        assertThat(newExercise.getGradingCriteria()).hasSize(1);
        assertThat(newExercise.getGradingCriteria().iterator().next().getTitle()).isEqualTo("Source criterion");
        assertThat(newExercise.getGradingCriteria().iterator().next()).isNotSameAs(source.getGradingCriteria().iterator().next());
    }

    @Test
    void keepsTheCallersOwnValuesAndBackfillsOnlyTheGaps() {
        TextExercise source = sourceWithContent();
        // A standalone import: the caller (edited body) already carries its own values, which must win over the source.
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        newExercise.setProblemStatement("Edited problem statement");
        newExercise.setDifficulty(DifficultyLevel.EASY);
        // assessmentType and gradingInstructions are left unset, so they must be backfilled from the source.

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getProblemStatement()).isEqualTo("Edited problem statement");
        assertThat(newExercise.getDifficulty()).isEqualTo(DifficultyLevel.EASY);
        assertThat(newExercise.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(newExercise.getGradingInstructions()).isEqualTo("Source grading instructions");
    }

    @Test
    void preservesCompetencyLinkProvenanceWhenRebindingLinksToTheImportedExercise() {
        TextExercise source = sourceWithContent();
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        CompetencyExerciseLink link = new CompetencyExerciseLink(new Competency(), newExercise, 0.5);
        link.setGeneratedByAi(true);
        newExercise.setCompetencyLinks(new HashSet<>(Set.of(link)));

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getCompetencyLinks()).singleElement().satisfies(copiedLink -> {
            assertThat(copiedLink.getExercise()).isSameAs(newExercise);
            assertThat(copiedLink.isGeneratedByAi()).isTrue();
        });
    }

    @Test
    void keepsAnEmptyGradingCriteriaCollectionTheCallerOwns() {
        TextExercise source = sourceWithContent();
        // A standalone import whose form had every grading criterion deleted: the caller's (initialized) empty collection
        // must win, otherwise the deleted criteria silently reappear on the imported exercise.
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        newExercise.setGradingCriteria(new HashSet<>());

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getGradingCriteria()).isEmpty();
    }

    @Test
    void clearsTheTeamAssignmentConfigUnlessTheExerciseIsATeamCourseExercise() {
        TextExercise source = sourceWithContent();
        // A client-supplied body may carry a team assignment config that must not be cascaded onto an individual exercise.
        TextExercise individualExercise = new TextExercise();
        individualExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        individualExercise.setMode(ExerciseMode.INDIVIDUAL);
        individualExercise.setTeamAssignmentConfig(teamAssignmentConfigWithId());

        service.copyBasis(individualExercise, source);

        assertThat(individualExercise.getTeamAssignmentConfig()).isNull();

        // The same holds for an exam exercise, which is always individual.
        TextExercise examExercise = new TextExercise();
        examExercise.setExerciseGroup(new ExerciseGroup());
        examExercise.setMode(ExerciseMode.TEAM);
        examExercise.setTeamAssignmentConfig(teamAssignmentConfigWithId());

        service.copyBasis(examExercise, source);

        assertThat(examExercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(examExercise.getTeamAssignmentConfig()).isNull();
    }

    @Test
    void copiesTheTeamAssignmentConfigAsAFreshEntityForTeamCourseExercises() {
        TextExercise source = sourceWithContent();
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        newExercise.setMode(ExerciseMode.TEAM);
        TeamAssignmentConfig callerConfig = teamAssignmentConfigWithId();
        newExercise.setTeamAssignmentConfig(callerConfig);

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getTeamAssignmentConfig()).isNotNull().isNotSameAs(callerConfig);
        assertThat(newExercise.getTeamAssignmentConfig().getId()).as("the copy must not reuse the caller's id").isNull();
        assertThat(newExercise.getTeamAssignmentConfig().getMinTeamSize()).isEqualTo(2);
    }

    @Test
    void prepareNewExerciseForImportClearsIdAndParticipationState() {
        TextExercise newExercise = new TextExercise();
        newExercise.setId(42L);
        // Pre-populate every collection the helper has to reset, so the assertions below cannot pass vacuously on the
        // empty collections a fresh entity already carries.
        newExercise.setStudentParticipations(new HashSet<>(Set.of(new StudentParticipation())));
        newExercise.setTutorParticipations(new HashSet<>(Set.of(new TutorParticipation())));
        newExercise.setExampleSubmissions(new HashSet<>(Set.of(new ExampleSubmission())));
        newExercise.setAttachments(new HashSet<>(Set.of(new Attachment())));
        newExercise.setPlagiarismCases(new HashSet<>(Set.of(new PlagiarismCase())));
        newExercise.setTeams(new HashSet<>(Set.of(new Team())));

        ExerciseImportService.prepareNewExerciseForImport(newExercise);

        assertThat(newExercise.getId()).isNull();
        assertThat(newExercise.getStudentParticipations()).isEmpty();
        assertThat(newExercise.getTutorParticipations()).isEmpty();
        assertThat(newExercise.getExampleSubmissions()).isEmpty();
        assertThat(newExercise.getAttachments()).isEmpty();
        assertThat(newExercise.getPlagiarismCases()).isEmpty();
        // teams has orphanRemoval enabled, so a carried-over team would fail to persist under the new owner.
        assertThat(newExercise.getTeams()).isEmpty();
    }

    @Test
    void keepsTheEditableFlagsTheCallerSubmitted() {
        TextExercise source = sourceWithContent();
        source.setSecondCorrectionEnabled(false);
        // The standalone import form owns these fields; develop reset them to the entity defaults because the new exercise
        // was built from scratch. They must survive the backfill unchanged.
        TextExercise newExercise = new TextExercise();
        newExercise.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        newExercise.setSecondCorrectionEnabled(true);
        newExercise.setAllowComplaintsForAutomaticAssessments(true);

        service.copyBasis(newExercise, source);

        assertThat(newExercise.getSecondCorrectionEnabled()).isTrue();
        assertThat(newExercise.getAllowComplaintsForAutomaticAssessments()).isTrue();
    }

    private static TeamAssignmentConfig teamAssignmentConfigWithId() {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setId(77L);
        config.setMinTeamSize(2);
        config.setMaxTeamSize(4);
        return config;
    }
}
