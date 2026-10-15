package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisActiveExerciseDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckCandidateDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckOwnerDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseContextDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupMembershipDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisOwnParticipationDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisParticipationRowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisTutorialGroupDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.util.MetisUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Runs every query and every guarded transition of the four Metis repositories once against the database: each query against rows that it has to find and
 * rows that it has to leave out, each transition against a row in its source state, which it has to move, and rows in other states, which it has to leave
 * alone.
 * <p>
 * Some queries are global (the claimable checks, the active exercises, the unrated answers, the release of every check being generated), and the database of
 * the bucket is shared with every other test class. Their results are therefore narrowed to the rows this class created before they are compared. Every
 * configuration a method switched on is switched off again afterwards, so that no check or answer of this class is ever claimed elsewhere.
 */
class MetisRepositoryQueryIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metisrepoquery";

    /** The due date of the fixture exercise. Every other time in the fixtures is an offset from it, in whole seconds, which the database keeps exactly. */
    private static final ZonedDateTime DUE = ZonedDateTime.parse("2024-03-01T10:00:00Z");

    /** An id no fixture hands out. */
    private static final long MISSING_ID = Long.MAX_VALUE / 2;

    private static final String HASH_A = "a".repeat(40);

    private static final String HASH_B = "b".repeat(40);

    private static final String HASH_C = "c".repeat(40);

    private static final String HASH_D = "d".repeat(40);

    private static final String HASH_E = "e".repeat(40);

    /** The columns are datetime(3) without a zone, so what comes back is the same instant, not necessarily the same zone. */
    private static final BiPredicate<ZonedDateTime, ZonedDateTime> SAME_INSTANT = (left, right) -> left.toInstant().equals(right.toInstant());

    private static final RecursiveComparisonConfiguration SAME_INSTANTS = RecursiveComparisonConfiguration.builder().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).build();

    @Autowired
    private MetisUtilService metisUtilService;

    @Autowired
    private MetisCheckRepository checkRepository;

    @Autowired
    private MetisQuestionRepository questionRepository;

    @Autowired
    private MetisExerciseConfigRepository configRepository;

    @Autowired
    private MetisGroupWindowRepository groupWindowRepository;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    private final List<Long> enabledExerciseIds = new ArrayList<>();

    private Course course;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 8, 2, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), DUE);
    }

    @AfterEach
    void switchOffTheEnabledConfigurations() {
        enabledExerciseIds.forEach(exerciseId -> metisUtilService.saveConfig(exerciseId, false, false, null, null));
        enabledExerciseIds.clear();
    }

    @Nested
    class CheckQueries {

        @Test
        void shouldFindGradedIndividualParticipationsOfStudentsWithoutCheckOrderedById() {
            // The candidate with an individual due date is created first: its id is the lower one although its login sorts after the other candidate's.
            var extendedCandidate = participation(exercise, "student3", DUE.plusMinutes(30));
            var candidate = participation(exercise, "student1");
            participation(exercise, "student2");
            userUtilService.enrollUserInCourse(user("student2"), course, CourseRole.TEACHING_ASSISTANT);
            participation(exercise, "tutor1");
            // tutor2 left the course: the participation stays, but no role in the course does, so only the join on the student role leaves it out.
            userUtilService.unenrollUserFromCourse(user("tutor2"), course);
            participation(exercise, "tutor2");
            saveParticipation(exercise, "student4", true, repositoryUri(exercise, "student4-practice"), null);
            saveParticipation(exercise, "student5", false, null, null);
            participation(exercise, "student6", DUE.plusHours(2));
            metisUtilService.saveCheck(participation(exercise, "student7").getId(), MetisCheckStatus.PENDING);
            teamParticipation(exercise, "student8");

            var expectedFirst = new MetisCheckCandidateDTO(extendedCandidate.getId(), user("student3").getId());
            var expectedSecond = new MetisCheckCandidateDTO(candidate.getId(), user("student1").getId());
            assertThat(checkRepository.findCheckCandidates(exercise.getId(), DUE.plusHours(1))).containsExactly(expectedFirst, expectedSecond);
            assertThat(checkRepository.findCheckCandidates(exercise.getId(), DUE.minusMinutes(1))).isEmpty();
        }

        @Test
        void shouldReadTheGenerationTargetWithTheEffectiveDueDate() {
            var extended = participation(exercise, "student1", DUE.plusHours(3));
            var regular = participation(exercise, "student2");
            MetisCheck extendedCheck = metisUtilService.saveGeneratedCheck(extended.getId(), MetisCheckStatus.READY, HASH_A);
            MetisCheck regularCheck = metisUtilService.saveCheck(regular.getId(), MetisCheckStatus.PENDING);

            var expectedExtended = new MetisGenerationTargetDTO(extendedCheck.getId(), MetisCheckStatus.READY, HASH_A, extended.getId(), user("student1").getId(),
                    extended.getRepositoryUri(), DUE.plusHours(3), exercise.getId(), course.getId());
            var expectedRegular = new MetisGenerationTargetDTO(regularCheck.getId(), MetisCheckStatus.PENDING, null, regular.getId(), user("student2").getId(),
                    regular.getRepositoryUri(), DUE, exercise.getId(), course.getId());
            assertThat(checkRepository.findGenerationTarget(extendedCheck.getId())).get().usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedExtended);
            assertThat(checkRepository.findGenerationTarget(regularCheck.getId())).get().usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedRegular);
            assertThat(checkRepository.findGenerationTarget(MISSING_ID)).isEmpty();
        }

        @Test
        void shouldListTheCommitsUpToTheDeadlineLatestFirstWithTheHigherIdWinningATie() {
            var participation = participation(exercise, "student1");
            submission(participation, DUE.minusHours(2), HASH_A);
            submission(participation, DUE.minusHours(1), HASH_B);
            submission(participation, DUE.minusHours(1), HASH_C);
            submission(participation, DUE, HASH_D);
            submission(participation, DUE.plusMinutes(1), HASH_E);
            submission(participation, DUE.minusMinutes(30), null);
            submission(participation(exercise, "student2"), DUE.minusMinutes(10), HASH_E);

            assertThat(checkRepository.findLatestCommitHashesUpTo(participation.getId(), DUE, Pageable.unpaged())).containsExactly(HASH_D, HASH_C, HASH_B, HASH_A);
            assertThat(checkRepository.findLatestCommitHashesUpTo(participation.getId(), DUE.minusMinutes(1), PageRequest.of(0, 1))).containsExactly(HASH_C);
            assertThat(checkRepository.findLatestCommitHashesUpTo(participation.getId(), DUE.minusHours(3), PageRequest.of(0, 1))).isEmpty();
        }

        @Test
        void shouldFindOnlyTheGradedParticipationsOfTheGivenUserWithTheirCheck() {
            var graded = participation(exercise, "student1", DUE.plusHours(2));
            saveParticipation(exercise, "student1", true, repositoryUri(exercise, "student1-practice"), null);
            MetisCheck check = metisUtilService.saveCheck(graded.getId(), MetisCheckStatus.READY);
            var withoutCheck = participation(exercise, "student2");

            var expectedWithCheck = new MetisOwnParticipationDTO(graded.getId(), user("student1").getId(), graded.getRepositoryUri(), DUE.plusHours(2), check.getId());
            var expectedWithoutCheck = new MetisOwnParticipationDTO(withoutCheck.getId(), user("student2").getId(), withoutCheck.getRepositoryUri(), DUE, null);
            var ownOfStudent1 = checkRepository.findOwnGradedParticipations(exercise.getId(), TEST_PREFIX + "student1");
            var ownOfStudent2 = checkRepository.findOwnGradedParticipations(exercise.getId(), TEST_PREFIX + "student2");
            assertThat(ownOfStudent1).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(List.of(expectedWithCheck));
            assertThat(ownOfStudent2).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(List.of(expectedWithoutCheck));
            assertThat(checkRepository.findOwnGradedParticipations(exercise.getId(), TEST_PREFIX + "student3")).isEmpty();
        }

        @Test
        void shouldReadOneDashboardRowPerCheckOfTheExerciseOrderedByLogin() {
            // Created first: the lower participation id, but the login that sorts last.
            var second = participation(exercise, "student2");
            var first = participation(exercise, "student1");
            MetisCheck completed = update(metisUtilService.saveGeneratedCheck(second.getId(), MetisCheckStatus.COMPLETED, HASH_A), check -> {
                check.setGeneratedAt(DUE.plusHours(1));
                check.setStartedAt(DUE.plusHours(2));
                check.setCompletedAt(DUE.plusHours(3));
            });
            MetisCheck skipped = failed(first, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);
            metisUtilService.saveCheck(participation(otherExercise("METISB", DUE), "student3").getId(), MetisCheckStatus.PENDING);

            User student1 = user("student1");
            User student2 = user("student2");
            var expectedFirst = new MetisParticipationRowDTO(first.getId(), student1.getId(), student1.getLogin(), student1.getFirstName(), student1.getLastName(), skipped.getId(),
                    MetisCheckStatus.SKIPPED, null, null, null, MetisFailureReason.NO_SUBMISSION.name(), null, null, null);
            var expectedSecond = new MetisParticipationRowDTO(second.getId(), student2.getId(), student2.getLogin(), student2.getFirstName(), student2.getLastName(),
                    completed.getId(), MetisCheckStatus.COMPLETED, HASH_A, MetisUtilService.TEST_LLM_MODEL, MetisUtilService.TEST_PROMPT_VERSION, null, DUE.plusHours(1),
                    DUE.plusHours(2), DUE.plusHours(3));
            assertThat(checkRepository.findDashboardRows(exercise.getId())).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(List.of(expectedFirst, expectedSecond));
        }

        @Test
        void shouldListPendingChecksOfEnabledExercisesByTheStudentsWindowStart() {
            // The configuration window opens at 12:00, so a student without a group window starts then; the second exercise has no window start and starts at its
            // due date, 10:30. The group windows open at 11:00 (early), 12:30 (middle) and 13:00 (late). They belong to the first exercise only, so the check of
            // student4 in the second exercise starts at 10:30 although student4 is in the early group.
            enable(exercise, DUE.plusHours(2), null);
            ProgrammingExercise withoutWindowStart = otherExercise("METISB", DUE.plusMinutes(30));
            enable(withoutWindowStart, null, null);
            ProgrammingExercise disabled = otherExercise("METISC", DUE);
            metisUtilService.saveConfig(disabled.getId(), false, false, null, null);
            TutorialGroup early = tutorialGroup("Metis early", user("student1"), user("student4"));
            TutorialGroup late = tutorialGroup("Metis late", user("student2"), user("student5"));
            TutorialGroup middle = tutorialGroup("Metis middle", user("student5"));
            metisUtilService.saveGroupWindow(exercise.getId(), early.getId(), DUE.plusHours(1), DUE.plusHours(5));
            metisUtilService.saveGroupWindow(exercise.getId(), late.getId(), DUE.plusHours(3), DUE.plusHours(6));
            metisUtilService.saveGroupWindow(exercise.getId(), middle.getId(), DUE.plusMinutes(150), DUE.plusHours(6));

            long inEarlyGroup = pendingCheck(exercise, "student1");
            long inLateGroup = pendingCheck(exercise, "student2");
            long withoutGroup = pendingCheck(exercise, "student3");
            long inLateAndMiddleGroup = pendingCheck(exercise, "student5");
            long ofExerciseWithoutWindowStart = pendingCheck(withoutWindowStart, "student4");
            long notDueYet = metisUtilService.saveCheck(participation(exercise, "student6", DUE.plusHours(2)).getId(), MetisCheckStatus.PENDING).getId();
            long ready = metisUtilService.saveCheck(participation(exercise, "student4").getId(), MetisCheckStatus.READY).getId();
            long ofDisabledExercise = pendingCheck(disabled, "student1");

            Set<Long> ownChecks = Set.of(inEarlyGroup, inLateGroup, withoutGroup, inLateAndMiddleGroup, ofExerciseWithoutWindowStart, notDueYet, ready, ofDisabledExercise);
            List<Long> claimable = checkRepository.findClaimableCheckIds(DUE.plusHours(1), Pageable.unpaged()).stream().filter(ownChecks::contains).toList();
            assertThat(claimable).containsExactly(ofExerciseWithoutWindowStart, inEarlyGroup, withoutGroup, inLateAndMiddleGroup, inLateGroup);
            assertThat(checkRepository.findClaimableCheckIds(DUE.plusHours(1), PageRequest.of(0, 1))).hasSize(1);
        }

        @Test
        void shouldFindTheChecksSkippedForTheReasonWithTheirStudents() {
            MetisCheck withoutConsent = failed(participation(exercise, "student1"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            failed(participation(exercise, "student2"), MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            failed(participation(exercise, "student3"), MetisCheckStatus.FAILED, MetisFailureReason.NO_AI_CONSENT);
            metisUtilService.saveCheck(participation(exercise, "student4").getId(), MetisCheckStatus.PENDING);
            ProgrammingExercise other = otherExercise("METISB", DUE);
            MetisCheck otherWithoutConsent = failed(participation(other, "student5"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);

            var expectedOwner = new MetisCheckOwnerDTO(withoutConsent.getId(), user("student1").getId());
            var expectedOtherOwner = new MetisCheckOwnerDTO(otherWithoutConsent.getId(), user("student5").getId());
            String noConsent = MetisFailureReason.NO_AI_CONSENT.name();
            assertThat(checkRepository.findSkippedChecksWithReason(List.of(exercise.getId()), noConsent)).containsExactly(expectedOwner);
            var ofBothExercises = checkRepository.findSkippedChecksWithReason(List.of(exercise.getId(), other.getId()), noConsent);
            assertThat(ofBothExercises).containsExactlyInAnyOrder(expectedOwner, expectedOtherOwner);
        }
    }

    @Nested
    class CheckTransitions {

        @Test
        void shouldClaimOnlyAPendingCheck() {
            MetisCheck pending = check("student1", MetisCheckStatus.PENDING);
            MetisCheck ready = check("student2", MetisCheckStatus.READY);

            assertThat(checkRepository.claimForGeneration(pending.getId())).isOne();
            assertThat(checkRepository.claimForGeneration(pending.getId())).isZero();
            assertThat(checkRepository.claimForGeneration(ready.getId())).isZero();
            assertThat(reload(pending).getStatus()).isEqualTo(MetisCheckStatus.GENERATING);
            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldReleaseOnlyACheckThatIsBeingGenerated() {
            MetisCheck generating = check("student1", MetisCheckStatus.GENERATING);
            MetisCheck ready = check("student2", MetisCheckStatus.READY);

            assertThat(checkRepository.releaseGeneration(generating.getId())).isOne();
            assertThat(checkRepository.releaseGeneration(ready.getId())).isZero();
            assertThat(reload(generating).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldReleaseEveryCheckThatIsBeingGenerated() {
            MetisCheck firstGenerating = check("student1", MetisCheckStatus.GENERATING);
            MetisCheck secondGenerating = check("student2", MetisCheckStatus.GENERATING);
            MetisCheck ready = check("student3", MetisCheckStatus.READY);

            assertThat(checkRepository.releaseAllGenerating()).isGreaterThanOrEqualTo(2);
            assertThat(reload(firstGenerating).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(secondGenerating).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldStoreTheSnapshotOnlyForACheckThatIsBeingGenerated() {
            // A reason left on a check in generation is not a state the service produces; it is set here to show that the transition clears it.
            MetisCheck generating = failed(participation(exercise, "student1"), MetisCheckStatus.GENERATING, MetisFailureReason.LLM_UNAVAILABLE);
            MetisCheck pending = check("student2", MetisCheckStatus.PENDING);

            assertThat(checkRepository.markGenerated(generating.getId(), MetisCheckStatus.IN_REVIEW, HASH_A, "model-a", "metis-v1", DUE)).isOne();
            assertThat(checkRepository.markGenerated(pending.getId(), MetisCheckStatus.READY, HASH_B, "model-b", "metis-v1", DUE)).isZero();

            MetisCheck generated = reload(generating);
            assertThat(generated.getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(generated.getCommitHash()).isEqualTo(HASH_A);
            assertThat(generated.getLlmModel()).isEqualTo("model-a");
            assertThat(generated.getPromptVersion()).isEqualTo("metis-v1");
            assertThat(generated.getGeneratedAt()).isEqualTo(DUE);
            assertThat(generated.getFailureReason()).isNull();
            MetisCheck untouched = reload(pending);
            assertThat(untouched.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(untouched.getCommitHash()).isNull();
            assertThat(untouched.getGeneratedAt()).isNull();
        }

        @Test
        void shouldEndOnlyACheckThatIsBeingGeneratedWithAReason() {
            MetisCheck failing = check("student1", MetisCheckStatus.GENERATING);
            MetisCheck skipping = update(check("student2", MetisCheckStatus.GENERATING), check -> check.setCommitHash(HASH_B));
            MetisCheck pending = check("student3", MetisCheckStatus.PENDING);

            assertThat(checkRepository.markUnavailable(failing.getId(), MetisCheckStatus.FAILED, MetisFailureReason.REPOSITORY_UNAVAILABLE.name(), HASH_A)).isOne();
            assertThat(checkRepository.markUnavailable(skipping.getId(), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION.name(), null)).isOne();
            assertThat(checkRepository.markUnavailable(pending.getId(), MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR.name(), null)).isZero();

            MetisCheck failed = reload(failing);
            assertThat(failed.getStatus()).isEqualTo(MetisCheckStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo(MetisFailureReason.REPOSITORY_UNAVAILABLE.name());
            assertThat(failed.getCommitHash()).isEqualTo(HASH_A);
            MetisCheck skipped = reload(skipping);
            assertThat(skipped.getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(skipped.getFailureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
            assertThat(skipped.getCommitHash()).isNull();
            assertThat(reload(pending).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(pending).getFailureReason()).isNull();
        }

        @Test
        void shouldStartAReadyCheckAndACheckInReviewOnce() {
            MetisCheck ready = check("student1", MetisCheckStatus.READY);
            MetisCheck inReview = check("student2", MetisCheckStatus.IN_REVIEW);
            MetisCheck pending = check("student3", MetisCheckStatus.PENDING);

            assertThat(checkRepository.markStarted(ready.getId(), DUE)).isOne();
            assertThat(checkRepository.markStarted(inReview.getId(), DUE)).isOne();
            assertThat(checkRepository.markStarted(pending.getId(), DUE)).isZero();
            assertThat(checkRepository.markStarted(ready.getId(), DUE.plusHours(1))).isZero();

            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(reload(ready).getStartedAt()).isEqualTo(DUE);
            assertThat(reload(inReview).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(reload(pending).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(pending).getStartedAt()).isNull();
        }

        @Test
        void shouldCompleteOnlyAStartedCheck() {
            MetisCheck inProgress = check("student1", MetisCheckStatus.IN_PROGRESS);
            MetisCheck ready = check("student2", MetisCheckStatus.READY);

            assertThat(checkRepository.markCompleted(inProgress.getId(), DUE)).isOne();
            assertThat(checkRepository.markCompleted(ready.getId(), DUE)).isZero();
            assertThat(checkRepository.markCompleted(inProgress.getId(), DUE.plusHours(1))).isZero();

            assertThat(reload(inProgress).getStatus()).isEqualTo(MetisCheckStatus.COMPLETED);
            assertThat(reload(inProgress).getCompletedAt()).isEqualTo(DUE);
            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(ready).getCompletedAt()).isNull();
        }

        @Test
        void shouldReleaseACheckInReviewOnlyOnceEveryMainQuestionIsApproved() {
            MetisCheck inReview = check("student1", MetisCheckStatus.IN_REVIEW);
            approved(mainQuestion(inReview, 1));
            MetisQuestion openMain = mainQuestion(inReview, 2);
            MetisCheck withOpenFollowUp = check("student2", MetisCheckStatus.IN_REVIEW);
            MetisQuestion approvedMain = approved(mainQuestion(withOpenFollowUp, 1));
            metisUtilService.saveFollowUp(withOpenFollowUp.getId(), 2, approvedMain.getId(), "And why?", 60);
            MetisCheck ready = check("student3", MetisCheckStatus.READY);

            assertThat(checkRepository.releaseIfAllMainQuestionsApproved(inReview.getId())).isZero();
            assertThat(reload(inReview).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            approved(openMain);
            assertThat(checkRepository.releaseIfAllMainQuestionsApproved(inReview.getId())).isOne();
            assertThat(reload(inReview).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(checkRepository.releaseIfAllMainQuestionsApproved(withOpenFollowUp.getId())).isOne();
            assertThat(reload(withOpenFollowUp).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(checkRepository.releaseIfAllMainQuestionsApproved(ready.getId())).isZero();
        }

        @Test
        void shouldReleaseTheFullyApprovedChecksInReviewOfTheExercise() {
            MetisCheck approvedCheck = check("student1", MetisCheckStatus.IN_REVIEW);
            approved(mainQuestion(approvedCheck, 1));
            MetisCheck openCheck = check("student2", MetisCheckStatus.IN_REVIEW);
            approved(mainQuestion(openCheck, 1));
            mainQuestion(openCheck, 2);
            MetisCheck otherExerciseCheck = metisUtilService.saveCheck(participation(otherExercise("METISB", DUE), "student3").getId(), MetisCheckStatus.IN_REVIEW);
            approved(mainQuestion(otherExerciseCheck, 1));
            // Neither of the next two checks has an unapproved main question (the skipped one has no questions at all), so only the guard on the status leaves them alone.
            MetisCheck skipped = failed(participation(exercise, "student4"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            MetisCheck completed = check("student5", MetisCheckStatus.COMPLETED);
            approved(mainQuestion(completed, 1));

            assertThat(checkRepository.releaseApprovedChecksOfExercise(exercise.getId())).isOne();
            assertThat(reload(approvedCheck).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(openCheck).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(reload(otherExerciseCheck).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(reload(skipped).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(reload(completed).getStatus()).isEqualTo(MetisCheckStatus.COMPLETED);
        }

        @Test
        void shouldReleaseEveryCheckInReviewOfTheExerciseWhetherApprovedOrNot() {
            MetisCheck approvedCheck = check("student1", MetisCheckStatus.IN_REVIEW);
            approved(mainQuestion(approvedCheck, 1));
            MetisCheck openCheck = check("student2", MetisCheckStatus.IN_REVIEW);
            mainQuestion(openCheck, 1);
            MetisCheck pending = check("student3", MetisCheckStatus.PENDING);
            MetisCheck otherExerciseCheck = metisUtilService.saveCheck(participation(otherExercise("METISB", DUE), "student4").getId(), MetisCheckStatus.IN_REVIEW);

            assertThat(checkRepository.releaseAllInReviewOfExercise(exercise.getId())).isEqualTo(2);
            assertThat(reload(approvedCheck).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(openCheck).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(pending).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(otherExerciseCheck).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
        }

        @Test
        void shouldReturnOnlyNotStartedReadyChecksWithAnUnapprovedMainQuestionToReview() {
            MetisCheck readyWithOpenQuestion = check("student1", MetisCheckStatus.READY);
            approved(mainQuestion(readyWithOpenQuestion, 1));
            mainQuestion(readyWithOpenQuestion, 2);
            MetisCheck fullyApproved = check("student2", MetisCheckStatus.READY);
            approved(mainQuestion(fullyApproved, 1));
            MetisCheck started = check("student3", MetisCheckStatus.IN_PROGRESS);
            mainQuestion(started, 1);
            // A ready check with a start time is not a state the service produces; it shows that the guard on the start time holds on its own.
            MetisCheck readyWithStartTime = update(check("student4", MetisCheckStatus.READY), check -> check.setStartedAt(DUE));
            mainQuestion(readyWithStartTime, 1);
            MetisCheck otherExerciseCheck = metisUtilService.saveCheck(participation(otherExercise("METISB", DUE), "student5").getId(), MetisCheckStatus.READY);
            mainQuestion(otherExerciseCheck, 1);
            // An extension puts a check back into the queue with the questions of its earlier generation, which only the next claim deletes. Nobody has started
            // it, so only the guard on the status keeps it in the queue.
            MetisCheck requeuedWithOldQuestion = check("student6", MetisCheckStatus.PENDING);
            mainQuestion(requeuedWithOldQuestion, 1);

            assertThat(checkRepository.returnUnstartedToReviewOfExercise(exercise.getId())).isOne();
            assertThat(reload(readyWithOpenQuestion).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(reload(fullyApproved).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(started).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(reload(readyWithStartTime).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(otherExerciseCheck).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(requeuedWithOldQuestion).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
        }

        @Test
        void shouldReturnOnlyANotStartedReadyCheckToReview() {
            MetisCheck ready = check("student1", MetisCheckStatus.READY);
            MetisCheck started = check("student2", MetisCheckStatus.IN_PROGRESS);
            MetisCheck readyWithStartTime = update(check("student3", MetisCheckStatus.READY), check -> check.setStartedAt(DUE));

            assertThat(checkRepository.returnToReview(ready.getId())).isOne();
            assertThat(checkRepository.returnToReview(ready.getId())).isZero();
            assertThat(checkRepository.returnToReview(started.getId())).isZero();
            assertThat(checkRepository.returnToReview(readyWithStartTime.getId())).isZero();
            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(reload(started).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(reload(readyWithStartTime).getStatus()).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        void shouldRequeueOnlyTheChecksOfTheExerciseThatEndedForTheGivenReasons() {
            MetisCheck unavailable = failed(participation(exercise, "student1"), MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE);
            MetisCheck withoutConsent = failed(participation(exercise, "student2"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            MetisCheck windowClosed = failed(participation(exercise, "student3"), MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            MetisCheck withoutSubmission = failed(participation(exercise, "student4"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);
            MetisCheck withoutOwnCode = failed(participation(exercise, "student5"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_OWN_CODE);
            MetisCheck ready = check("student6", MetisCheckStatus.READY);
            ProgrammingExercise other = otherExercise("METISB", DUE);
            MetisCheck otherExerciseCheck = failed(participation(other, "student7"), MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE);

            assertThat(checkRepository.requeueChecksOfExercise(exercise.getId(), List.of(MetisFailureReason.WINDOW_CLOSED.name()))).isOne();
            assertThat(reload(windowClosed).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(withoutConsent).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);

            assertThat(checkRepository.requeueChecksOfExercise(exercise.getId(), MetisFailureReason.REQUEUED_BY_GENERATE_NOW)).isEqualTo(2);
            for (MetisCheck requeued : List.of(unavailable, withoutConsent, windowClosed)) {
                assertThat(reload(requeued).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
                assertThat(reload(requeued).getFailureReason()).isNull();
            }
            assertThat(reload(withoutSubmission).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(reload(withoutSubmission).getFailureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
            assertThat(reload(withoutOwnCode).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(reload(ready).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(otherExerciseCheck).getStatus()).isEqualTo(MetisCheckStatus.FAILED);
        }

        @Test
        void shouldRequeueOnlyTheListedChecksThatAreStillSkippedForTheReason() {
            MetisCheck listed = failed(participation(exercise, "student1"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            MetisCheck notListed = failed(participation(exercise, "student2"), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            MetisCheck otherReason = failed(participation(exercise, "student3"), MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            MetisCheck notSkipped = failed(participation(exercise, "student4"), MetisCheckStatus.FAILED, MetisFailureReason.NO_AI_CONSENT);

            List<Long> checkIds = List.of(listed.getId(), otherReason.getId(), notSkipped.getId());
            assertThat(checkRepository.requeueSkippedChecks(checkIds, MetisFailureReason.NO_AI_CONSENT.name())).isOne();
            assertThat(reload(listed).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(reload(listed).getFailureReason()).isNull();
            assertThat(reload(notListed).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(reload(otherReason).getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(reload(notSkipped).getStatus()).isEqualTo(MetisCheckStatus.FAILED);
        }

        @Test
        void shouldResetTheNotStartedChecksWhoseEffectiveDueDateLiesInTheFuture() {
            ZonedDateTime now = DUE.plusHours(1);
            ZonedDateTime extendedDueDate = DUE.plusHours(2);
            MetisCheck ready = metisUtilService.saveCheck(participation(exercise, "student1", extendedDueDate).getId(), MetisCheckStatus.READY);
            MetisCheck inReview = metisUtilService.saveCheck(participation(exercise, "student2", extendedDueDate).getId(), MetisCheckStatus.IN_REVIEW);
            MetisCheck skipped = failed(participation(exercise, "student3", extendedDueDate), MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);
            MetisCheck started = metisUtilService.saveCheck(participation(exercise, "student4", extendedDueDate).getId(), MetisCheckStatus.IN_PROGRESS);
            MetisCheck generating = metisUtilService.saveCheck(participation(exercise, "student5", extendedDueDate).getId(), MetisCheckStatus.GENERATING);
            MetisCheck dueDatePassed = check("student6", MetisCheckStatus.READY);
            ProgrammingExercise postponed = otherExercise("METISB", extendedDueDate);
            MetisCheck ofPostponedExercise = metisUtilService.saveCheck(participation(postponed, "student7").getId(), MetisCheckStatus.FAILED);

            assertThat(checkRepository.resetChecksWithFutureDueDate(exercise.getId(), now)).isEqualTo(3);
            for (MetisCheck reset : List.of(ready, inReview, skipped)) {
                assertThat(reload(reset).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
                assertThat(reload(reset).getFailureReason()).isNull();
            }
            assertThat(reload(started).getStatus()).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(reload(generating).getStatus()).isEqualTo(MetisCheckStatus.GENERATING);
            assertThat(reload(dueDatePassed).getStatus()).isEqualTo(MetisCheckStatus.READY);
            assertThat(reload(ofPostponedExercise).getStatus()).isEqualTo(MetisCheckStatus.FAILED);

            assertThat(checkRepository.resetChecksWithFutureDueDate(postponed.getId(), now)).isOne();
            assertThat(reload(ofPostponedExercise).getStatus()).isEqualTo(MetisCheckStatus.PENDING);
        }
    }

    @Nested
    class QuestionQueries {

        @Test
        void shouldLoadTheQuestionsOfTheExerciseOrderedByCheckAndOrder() {
            MetisCheck firstCheck = check("student1", MetisCheckStatus.READY);
            MetisCheck secondCheck = check("student2", MetisCheckStatus.READY);
            MetisQuestion firstOfSecondCheck = mainQuestion(secondCheck, 1);
            MetisQuestion secondOfFirstCheck = mainQuestion(firstCheck, 2);
            MetisQuestion firstOfFirstCheck = mainQuestion(firstCheck, 1);
            MetisCheck otherExerciseCheck = metisUtilService.saveCheck(participation(otherExercise("METISB", DUE), "student3").getId(), MetisCheckStatus.READY);
            mainQuestion(otherExerciseCheck, 1);

            List<Long> expectedOrder = List.of(firstOfFirstCheck.getId(), secondOfFirstCheck.getId(), firstOfSecondCheck.getId());
            assertThat(questionRepository.findAllByExerciseId(exercise.getId())).extracting(MetisQuestion::getId).containsExactlyElementsOf(expectedOrder);
        }

        @Test
        void shouldFindAQuestionOnlyThroughItsOwnExercise() {
            MetisQuestion question = mainQuestion(check("student1", MetisCheckStatus.READY), 1);
            ProgrammingExercise other = otherExercise("METISB", DUE);

            assertThat(questionRepository.findByIdAndExerciseId(question.getId(), exercise.getId())).map(MetisQuestion::getId).contains(question.getId());
            assertThat(questionRepository.findByIdAndExerciseIdElseThrow(question.getId(), exercise.getId()).getId()).isEqualTo(question.getId());
            assertThat(questionRepository.findByIdAndExerciseId(question.getId(), other.getId())).isEmpty();
            assertThatThrownBy(() -> questionRepository.findByIdAndExerciseIdElseThrow(question.getId(), other.getId())).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        void shouldFindTheUnratedAnswersOfRecentlyStartedChecksOfEnabledExercisesOnceTheySettled() {
            enable(exercise, null, null);
            MetisCheck recent = update(check("student1", MetisCheckStatus.IN_PROGRESS), check -> check.setStartedAt(DUE.plusHours(1)));
            MetisQuestion unrated = answered(mainQuestion(recent, 1), DUE.plusMinutes(70));
            MetisQuestion alreadyRated = rated(answered(mainQuestion(recent, 2), DUE.plusMinutes(70)));
            MetisQuestion answeredAfterTheBound = answered(mainQuestion(recent, 3), DUE.plusMinutes(100));
            MetisQuestion unanswered = shown(mainQuestion(recent, 4), DUE.plusMinutes(80));
            MetisQuestion excluded = answered(mainQuestion(recent, 5), DUE.plusMinutes(65));
            MetisQuestion anotherUnrated = answered(mainQuestion(recent, 6), DUE.plusMinutes(66));
            MetisCheck old = update(check("student2", MetisCheckStatus.IN_PROGRESS), check -> check.setStartedAt(DUE.minusDays(2)));
            MetisQuestion ofOldCheck = answered(mainQuestion(old, 1), DUE.minusDays(2).plusMinutes(10));
            ProgrammingExercise disabled = otherExercise("METISB", DUE);
            metisUtilService.saveConfig(disabled.getId(), false, false, null, null);
            MetisCheck ofDisabledExercise = metisUtilService.saveCheck(participation(disabled, "student3").getId(), MetisCheckStatus.IN_PROGRESS);
            ofDisabledExercise = update(ofDisabledExercise, check -> check.setStartedAt(DUE.plusHours(1)));
            MetisQuestion ofDisabled = answered(mainQuestion(ofDisabledExercise, 1), DUE.plusMinutes(70));

            // Every question this method created takes part in the comparison, so that a rated, an unanswered or a too recent answer would show up in it.
            Set<Long> ownQuestions = Set.of(unrated.getId(), alreadyRated.getId(), answeredAfterTheBound.getId(), unanswered.getId(), excluded.getId(), anotherUnrated.getId(),
                    ofOldCheck.getId(), ofDisabled.getId());
            List<Long> excludedIds = List.of(-1L, excluded.getId());
            List<Long> unratedIds = questionRepository.findUnratedAnsweredQuestionIds(DUE.minusDays(1), DUE.plusMinutes(90), excludedIds, Pageable.unpaged());
            assertThat(unratedIds.stream().filter(ownQuestions::contains).toList()).containsExactly(unrated.getId(), anotherUnrated.getId());
            assertThat(questionRepository.findUnratedAnsweredQuestionIds(DUE.minusDays(1), DUE.plusMinutes(90), excludedIds, PageRequest.of(0, 1))).hasSize(1);
        }
    }

    @Nested
    class QuestionTransitions {

        @Test
        void shouldApproveAMainQuestionOnce() {
            MetisCheck check = check("student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion main = mainQuestion(check, 1);
            MetisQuestion followUp = metisUtilService.saveFollowUp(check.getId(), 2, main.getId(), "And why?", 60);

            assertThat(questionRepository.approve(main.getId(), DUE)).isOne();
            assertThat(questionRepository.approve(main.getId(), DUE.plusHours(1))).isZero();
            assertThat(questionRepository.approve(followUp.getId(), DUE)).isZero();
            assertThat(reload(main).getApprovedAt()).isEqualTo(DUE);
            assertThat(reload(followUp).getApprovedAt()).isNull();
        }

        @Test
        void shouldApproveTheOpenMainQuestionsOfTheExerciseOnly() {
            MetisCheck firstCheck = check("student1", MetisCheckStatus.IN_REVIEW);
            MetisQuestion open = mainQuestion(firstCheck, 1);
            MetisQuestion approvedBefore = approved(mainQuestion(firstCheck, 2));
            MetisQuestion followUp = metisUtilService.saveFollowUp(firstCheck.getId(), 3, open.getId(), "And why?", 60);
            MetisQuestion openOfSecondCheck = mainQuestion(check("student2", MetisCheckStatus.IN_REVIEW), 1);
            MetisCheck otherExerciseCheck = metisUtilService.saveCheck(participation(otherExercise("METISB", DUE), "student3").getId(), MetisCheckStatus.IN_REVIEW);
            MetisQuestion openOfOtherExercise = mainQuestion(otherExerciseCheck, 1);

            assertThat(questionRepository.approveAllOpenMainQuestionsOfExercise(exercise.getId(), DUE.plusHours(1))).isEqualTo(2);
            assertThat(reload(open).getApprovedAt()).isEqualTo(DUE.plusHours(1));
            assertThat(reload(openOfSecondCheck).getApprovedAt()).isEqualTo(DUE.plusHours(1));
            assertThat(reload(approvedBefore).getApprovedAt()).isEqualTo(DUE);
            assertThat(reload(followUp).getApprovedAt()).isNull();
            assertThat(reload(openOfOtherExercise).getApprovedAt()).isNull();
        }

        @Test
        void shouldEditOnlyAnUnshownMainQuestionOfANotStartedCheckKeepingItsApproval() {
            MetisCheck ready = check("student1", MetisCheckStatus.READY);
            MetisQuestion approvedMain = approved(mainQuestion(ready, 1));
            MetisQuestion shownMain = shown(mainQuestion(ready, 2), DUE);
            MetisQuestion followUp = metisUtilService.saveFollowUp(ready.getId(), 3, approvedMain.getId(), "And why?", 60);
            MetisQuestion ofCheckInReview = mainQuestion(check("student2", MetisCheckStatus.IN_REVIEW), 1);
            MetisQuestion ofStartedCheck = mainQuestion(check("student3", MetisCheckStatus.IN_PROGRESS), 2);
            MetisQuestion ofCheckInGeneration = mainQuestion(check("student4", MetisCheckStatus.GENERATING), 1);

            assertThat(questionRepository.updateTextIfNotShown(approvedMain.getId(), "Edited")).isOne();
            assertThat(questionRepository.updateTextIfNotShown(ofCheckInReview.getId(), "Edited")).isOne();
            assertThat(questionRepository.updateTextIfNotShown(shownMain.getId(), "Edited")).isZero();
            assertThat(questionRepository.updateTextIfNotShown(followUp.getId(), "Edited")).isZero();
            assertThat(questionRepository.updateTextIfNotShown(ofStartedCheck.getId(), "Edited")).isZero();
            assertThat(questionRepository.updateTextIfNotShown(ofCheckInGeneration.getId(), "Edited")).isZero();

            assertThat(reload(approvedMain).getQuestionText()).isEqualTo("Edited");
            assertThat(reload(approvedMain).getApprovedAt()).isEqualTo(DUE);
            assertThat(reload(ofCheckInReview).getQuestionText()).isEqualTo("Edited");
            for (MetisQuestion unchanged : List.of(shownMain, followUp, ofStartedCheck, ofCheckInGeneration)) {
                assertThat(reload(unchanged).getQuestionText()).isEqualTo(unchanged.getQuestionText());
            }
        }

        @Test
        void shouldReplaceARegeneratedQuestionOnlyIfItIsUnchangedUnshownAndItsCheckNotStarted() {
            MetisCheck ready = check("student1", MetisCheckStatus.READY);
            MetisQuestion regenerated = approved(mainQuestion(ready, 1));
            MetisQuestion shownMain = shown(mainQuestion(ready, 2), DUE);
            MetisQuestion ofStartedCheck = mainQuestion(check("student2", MetisCheckStatus.IN_PROGRESS), 2);
            // With a review required, the regeneration returns the check to review before it asks the LLM, so the check is in review when the text is stored.
            MetisQuestion ofCheckInReview = approved(mainQuestion(check("student3", MetisCheckStatus.IN_REVIEW), 1));
            // A check in generation has not been started either, so only the guard on the status refuses its question.
            MetisQuestion ofCheckInGeneration = mainQuestion(check("student4", MetisCheckStatus.GENERATING), 1);

            // An editor changed the text after the regeneration read it
            assertThat(questionRepository.replaceAfterRegeneration(regenerated.getId(), "Regenerated", 200, 0, "Text before the edit")).isZero();
            assertThat(questionRepository.replaceAfterRegeneration(regenerated.getId(), "Regenerated", 200, 0, regenerated.getQuestionText())).isOne();
            // Another regeneration finished after this one read the question
            assertThat(questionRepository.replaceAfterRegeneration(regenerated.getId(), "Regenerated again", 210, 0, "Regenerated")).isZero();
            assertThat(questionRepository.replaceAfterRegeneration(shownMain.getId(), "Regenerated", 200, 0, shownMain.getQuestionText())).isZero();
            assertThat(questionRepository.replaceAfterRegeneration(ofStartedCheck.getId(), "Regenerated", 200, 0, ofStartedCheck.getQuestionText())).isZero();
            assertThat(questionRepository.replaceAfterRegeneration(ofCheckInReview.getId(), "Regenerated in review", 220, 0, ofCheckInReview.getQuestionText())).isOne();
            assertThat(questionRepository.replaceAfterRegeneration(ofCheckInGeneration.getId(), "Regenerated", 200, 0, ofCheckInGeneration.getQuestionText())).isZero();

            MetisQuestion replaced = reload(regenerated);
            assertThat(replaced.getQuestionText()).isEqualTo("Regenerated");
            assertThat(replaced.getTimeLimitSeconds()).isEqualTo(200);
            assertThat(replaced.getRegenerationCount()).isOne();
            assertThat(replaced.getApprovedAt()).isNull();
            MetisQuestion replacedInReview = reload(ofCheckInReview);
            assertThat(replacedInReview.getQuestionText()).isEqualTo("Regenerated in review");
            assertThat(replacedInReview.getTimeLimitSeconds()).isEqualTo(220);
            assertThat(replacedInReview.getRegenerationCount()).isOne();
            assertThat(replacedInReview.getApprovedAt()).isNull();
            assertThat(reload(shownMain).getRegenerationCount()).isZero();
            for (MetisQuestion unchanged : List.of(ofStartedCheck, ofCheckInGeneration)) {
                assertThat(reload(unchanged).getQuestionText()).isEqualTo(unchanged.getQuestionText());
                assertThat(reload(unchanged).getRegenerationCount()).isZero();
            }
        }

        @Test
        void shouldShowAnswerAndRateAQuestionOnceEach() {
            MetisCheck check = check("student1", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion question = mainQuestion(check, 1);
            MetisQuestion unshown = mainQuestion(check, 2);

            assertThat(questionRepository.saveAnswer(question.getId(), "Too early", DUE)).isZero();
            assertThat(questionRepository.markShown(question.getId(), DUE)).isOne();
            assertThat(questionRepository.markShown(question.getId(), DUE.plusMinutes(1))).isZero();
            assertThat(questionRepository.saveRating(question.getId(), 2, "Not answered yet", DUE)).isZero();
            assertThat(questionRepository.saveAnswer(question.getId(), "The loop sorts the left half", DUE.plusMinutes(2))).isOne();
            assertThat(questionRepository.saveAnswer(question.getId(), "A second answer", DUE.plusMinutes(3))).isZero();
            assertThat(questionRepository.saveRating(question.getId(), 4, "Names the invariant", DUE.plusMinutes(4))).isOne();
            assertThat(questionRepository.saveRating(question.getId(), 1, "A second rating", DUE.plusMinutes(5))).isZero();
            assertThat(questionRepository.saveAnswer(unshown.getId(), "Not shown yet", DUE)).isZero();

            MetisQuestion answered = reload(question);
            assertThat(answered.getShownAt()).isEqualTo(DUE);
            assertThat(answered.getAnswerText()).isEqualTo("The loop sorts the left half");
            assertThat(answered.getAnsweredAt()).isEqualTo(DUE.plusMinutes(2));
            assertThat(answered.getLlmScore()).isEqualTo(4);
            assertThat(answered.getLlmRationale()).isEqualTo("Names the invariant");
            assertThat(answered.getLlmRatedAt()).isEqualTo(DUE.plusMinutes(4));
            assertThat(reload(unshown).getAnswerText()).isNull();
            assertThat(reload(unshown).getAnsweredAt()).isNull();
        }

        @Test
        void shouldDeleteTheLeftoverQuestionsOfOneCheckOnly() {
            MetisCheck leftover = check("student1", MetisCheckStatus.GENERATING);
            mainQuestion(leftover, 1);
            mainQuestion(leftover, 2);
            MetisCheck other = check("student2", MetisCheckStatus.READY);
            MetisQuestion kept = mainQuestion(other, 1);

            assertThat(questionRepository.deleteAllOfCheck(leftover.getId())).isEqualTo(2);
            assertThat(questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(leftover.getId())).isEmpty();
            assertThat(questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(other.getId())).extracting(MetisQuestion::getId).containsExactly(kept.getId());
        }
    }

    @Nested
    class ConfigQueries {

        @Test
        void shouldTellWhetherTheExerciseHasAnEnabledCheck() {
            assertThat(configRepository.existsByExerciseIdAndEnabledTrue(exercise.getId())).isFalse();
            metisUtilService.saveConfig(exercise.getId(), false, false, null, null);
            assertThat(configRepository.existsByExerciseIdAndEnabledTrue(exercise.getId())).isFalse();
            enable(exercise, null, null);
            assertThat(configRepository.existsByExerciseIdAndEnabledTrue(exercise.getId())).isTrue();
        }

        @Test
        void shouldFindTheEnabledExercisesWhoseLatestDueDateOrWindowEndLiesWithinTheHorizon() {
            ZonedDateTime longBefore = DUE.minusDays(10);
            exerciseUtilService.updateExerciseDueDate(exercise.getId(), DUE.plusHours(1));
            enable(exercise, null, null);
            ProgrammingExercise extended = otherExercise("METISB", longBefore);
            enable(extended, null, null);
            participation(extended, "student1", DUE.plusHours(1));
            ProgrammingExercise lateWindow = otherExercise("METISC", longBefore);
            enable(lateWindow, null, DUE.plusHours(1));
            ProgrammingExercise lateGroupWindow = otherExercise("METISD", longBefore);
            enable(lateGroupWindow, null, null);
            TutorialGroup group = tutorialGroup("Metis group");
            metisUtilService.saveGroupWindow(lateGroupWindow.getId(), group.getId(), DUE.minusHours(1), DUE.plusHours(1));
            ProgrammingExercise outsideTheHorizon = otherExercise("METISE", longBefore);
            enable(outsideTheHorizon, null, DUE.minusDays(5));
            participation(outsideTheHorizon, "student1", DUE.minusDays(5));
            metisUtilService.saveGroupWindow(outsideTheHorizon.getId(), group.getId(), DUE.minusDays(6), DUE.minusDays(5));

            Set<Long> ownExercises = Set.of(exercise.getId(), extended.getId(), lateWindow.getId(), lateGroupWindow.getId(), outsideTheHorizon.getId());
            var activeByDueDate = new MetisActiveExerciseDTO(exercise.getId(), course.getId(), DUE.plusHours(1));
            var activeByIndividualDueDate = new MetisActiveExerciseDTO(extended.getId(), course.getId(), longBefore);
            var activeByWindowEnd = new MetisActiveExerciseDTO(lateWindow.getId(), course.getId(), longBefore);
            var activeByGroupWindowEnd = new MetisActiveExerciseDTO(lateGroupWindow.getId(), course.getId(), longBefore);
            List<MetisActiveExerciseDTO> expected = List.of(activeByDueDate, activeByIndividualDueDate, activeByWindowEnd, activeByGroupWindowEnd);
            assertThat(activeExercises(ownExercises)).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expected);

            // A configuration that is switched off, or an exercise without a due date, takes the exercise out.
            metisUtilService.saveConfig(lateWindow.getId(), false, false, null, DUE.plusHours(1));
            exerciseUtilService.updateExerciseDueDate(extended.getId(), null);
            assertThat(activeExercises(ownExercises)).extracting(MetisActiveExerciseDTO::exerciseId).containsExactly(exercise.getId(), lateGroupWindow.getId());
        }

        @Test
        void shouldReadTheFactsAndTheContextOfAProgrammingExercise() {
            var expectedFacts = new MetisExerciseFactsDTO(exercise.getId(), course.getId(), ExerciseMode.INDIVIDUAL, DUE, exercise.getTitle(), exercise.getShortName());
            var expectedContext = new MetisExerciseContextDTO(exercise.getId(), exercise.getTitle(), exercise.getProblemStatement(), ProgrammingLanguage.JAVA);

            assertThat(configRepository.findExerciseFacts(exercise.getId())).get().usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedFacts);
            assertThat(configRepository.findExerciseContext(exercise.getId())).contains(expectedContext);
            assertThat(configRepository.findExerciseFacts(MISSING_ID)).isEmpty();
            assertThat(configRepository.findExerciseContext(MISSING_ID)).isEmpty();
        }
    }

    @Nested
    class GroupWindowQueries {

        @Test
        void shouldFindTheWindowsOfTheGroupsOfAStudentEarliestFirst() {
            User student1 = user("student1");
            TutorialGroup tuesday = tutorialGroup("Metis Tuesday", student1, user("student2"));
            TutorialGroup monday = tutorialGroup("Metis Monday", student1);
            TutorialGroup friday = tutorialGroup("Metis Friday", student1);
            TutorialGroup windowOfAnotherExercise = tutorialGroup("Metis Wednesday", student1);
            metisUtilService.saveGroupWindow(exercise.getId(), tuesday.getId(), DUE.plusHours(3), DUE.plusHours(5));
            metisUtilService.saveGroupWindow(exercise.getId(), monday.getId(), DUE.plusHours(1), DUE.plusHours(4));
            metisUtilService.saveGroupWindow(exercise.getId(), friday.getId(), DUE.plusHours(1), DUE.plusHours(2));
            metisUtilService.saveGroupWindow(otherExercise("METISB", DUE).getId(), windowOfAnotherExercise.getId(), DUE, DUE.plusHours(1));

            var fridayWindow = new MetisStudentGroupWindowDTO(friday.getId(), "Metis Friday", DUE.plusHours(1), DUE.plusHours(2));
            var mondayWindow = new MetisStudentGroupWindowDTO(monday.getId(), "Metis Monday", DUE.plusHours(1), DUE.plusHours(4));
            var tuesdayWindow = new MetisStudentGroupWindowDTO(tuesday.getId(), "Metis Tuesday", DUE.plusHours(3), DUE.plusHours(5));
            var expectedOfStudent1 = List.of(fridayWindow, mondayWindow, tuesdayWindow);
            var expectedOfStudent2 = List.of(tuesdayWindow);
            var windowsOfStudent1 = groupWindowRepository.findGroupWindowsOfStudent(exercise.getId(), student1.getId());
            var windowsOfStudent2 = groupWindowRepository.findGroupWindowsOfStudent(exercise.getId(), user("student2").getId());
            assertThat(windowsOfStudent1).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedOfStudent1);
            assertThat(windowsOfStudent2).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedOfStudent2);
            assertThat(groupWindowRepository.findGroupWindowsOfStudent(exercise.getId(), user("student3").getId())).isEmpty();
        }

        @Test
        void shouldReadTheTutorialGroupsAndTheirMembersOfACourseOnly() {
            User student1 = user("student1");
            User student2 = user("student2");
            TutorialGroup tuesday = tutorialGroup("Metis Tuesday", student1, student2);
            TutorialGroup monday = tutorialGroup("Metis Monday", student1);
            TutorialGroup withoutMembers = tutorialGroup("Metis Friday");
            Course otherCourse = courseUtilService.addEmptyCourse();
            TutorialGroup ofOtherCourse = tutorialGroupUtilService.createAndSaveTutorialGroup(otherCourse.getId(), "Metis Monday", "", 10, false, "Garching", "English",
                    user("tutor1"), Set.of(student1));

            var student1InTuesday = new MetisGroupMembershipDTO(student1.getId(), tuesday.getId(), "Metis Tuesday");
            var student2InTuesday = new MetisGroupMembershipDTO(student2.getId(), tuesday.getId(), "Metis Tuesday");
            var student1InMonday = new MetisGroupMembershipDTO(student1.getId(), monday.getId(), "Metis Monday");
            assertThat(groupWindowRepository.findGroupMembershipsByCourseId(course.getId())).containsExactlyInAnyOrder(student1InTuesday, student2InTuesday, student1InMonday);
            var fridayGroup = new MetisTutorialGroupDTO(withoutMembers.getId(), "Metis Friday");
            var mondayGroup = new MetisTutorialGroupDTO(monday.getId(), "Metis Monday");
            var tuesdayGroup = new MetisTutorialGroupDTO(tuesday.getId(), "Metis Tuesday");
            assertThat(groupWindowRepository.findTutorialGroupsByCourseId(course.getId())).containsExactly(fridayGroup, mondayGroup, tuesdayGroup);
            List<Long> requestedGroups = List.of(tuesday.getId(), withoutMembers.getId(), ofOtherCourse.getId());
            assertThat(groupWindowRepository.countTutorialGroupsOfCourse(course.getId(), requestedGroups)).isEqualTo(2);
        }
    }

    private User user(String suffix) {
        return userUtilService.getUserByLogin(TEST_PREFIX + suffix);
    }

    private ProgrammingExercise otherExercise(String shortName, @Nullable ZonedDateTime dueDate) {
        ProgrammingExercise other = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, ProgrammingLanguage.JAVA, "Metis " + shortName, shortName, null);
        exerciseUtilService.updateExerciseDueDate(other.getId(), dueDate);
        return other;
    }

    private void enable(ProgrammingExercise exerciseToEnable, @Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd) {
        metisUtilService.saveConfig(exerciseToEnable.getId(), true, false, windowStart, windowEnd);
        enabledExerciseIds.add(exerciseToEnable.getId());
    }

    private List<MetisActiveExerciseDTO> activeExercises(Set<Long> ownExercises) {
        return configRepository.findActiveExercises(DUE).stream().filter(active -> ownExercises.contains(active.exerciseId()))
                .sorted(Comparator.comparingLong(MetisActiveExerciseDTO::exerciseId)).toList();
    }

    private TutorialGroup tutorialGroup(String title, User... students) {
        return tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), title, "", 10, false, "Garching", "English", user("tutor1"), Set.of(students));
    }

    /**
     * A graded participation of a user in an exercise, with a repository and without an individual due date.
     */
    private ProgrammingExerciseStudentParticipation participation(ProgrammingExercise participationExercise, String userSuffix) {
        return participation(participationExercise, userSuffix, null);
    }

    private ProgrammingExerciseStudentParticipation participation(ProgrammingExercise participationExercise, String userSuffix, @Nullable ZonedDateTime individualDueDate) {
        return saveParticipation(participationExercise, userSuffix, false, repositoryUri(participationExercise, userSuffix), individualDueDate);
    }

    /**
     * Saves a participation directly, so that the test controls every column the queries read. A practice participation gets attempt 1, as the server gives it,
     * which keeps it apart from the graded one in the unique key of the table.
     */
    private ProgrammingExerciseStudentParticipation saveParticipation(ProgrammingExercise participationExercise, String userSuffix, boolean practice,
            @Nullable String repositoryUri, @Nullable ZonedDateTime individualDueDate) {
        var participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(participationExercise, user(userSuffix));
        participation.setPracticeMode(practice);
        participation.setAttempt(practice ? 1 : 0);
        participation.setRepositoryUri(repositoryUri);
        participation.setIndividualDueDate(individualDueDate);
        return programmingExerciseStudentParticipationRepository.save(participation);
    }

    private ProgrammingExerciseStudentParticipation teamParticipation(ProgrammingExercise participationExercise, String memberSuffix) {
        Team team = teamUtilService.createTeam(Set.of(user(memberSuffix)), user("tutor1"), participationExercise, "metisteam");
        var participation = ParticipationFactory.generateTeamProgrammingExerciseStudentParticipation(participationExercise, team);
        participation.setRepositoryUri(repositoryUri(participationExercise, "metisteam"));
        return programmingExerciseStudentParticipationRepository.save(participation);
    }

    private static String repositoryUri(ProgrammingExercise participationExercise, String repositorySuffix) {
        String repositoryName = (participationExercise.getProjectKey() + "-" + TEST_PREFIX + repositorySuffix).toLowerCase(Locale.ROOT);
        return "http://localhost/git/" + participationExercise.getProjectKey() + "/" + repositoryName + ".git";
    }

    private void submission(ProgrammingExerciseStudentParticipation participation, ZonedDateTime submissionDate, @Nullable String commitHash) {
        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true, commitHash, SubmissionType.MANUAL);
        submission.setSubmissionDate(submissionDate);
        submission.setParticipation(participation);
        programmingSubmissionRepository.save(submission);
    }

    /**
     * A check of the fixture exercise for a new graded participation of the user.
     */
    private MetisCheck check(String userSuffix, MetisCheckStatus status) {
        return metisUtilService.saveCheck(participation(exercise, userSuffix).getId(), status);
    }

    private long pendingCheck(ProgrammingExercise checkExercise, String userSuffix) {
        return metisUtilService.saveCheck(participation(checkExercise, userSuffix).getId(), MetisCheckStatus.PENDING).getId();
    }

    private MetisCheck failed(ProgrammingExerciseStudentParticipation participation, MetisCheckStatus status, MetisFailureReason reason) {
        return update(metisUtilService.saveCheck(participation.getId(), status), check -> check.setFailureReason(reason.name()));
    }

    private MetisCheck update(MetisCheck check, Consumer<MetisCheck> change) {
        change.accept(check);
        return checkRepository.save(check);
    }

    private MetisCheck reload(MetisCheck check) {
        return checkRepository.findByIdElseThrow(check.getId());
    }

    private MetisQuestion mainQuestion(MetisCheck check, int order) {
        return metisUtilService.saveMainQuestion(check.getId(), order, "src/Sorter" + order + ".java", 10 + order, "What does part " + order + " of the sorter do?", 120);
    }

    private MetisQuestion approved(MetisQuestion question) {
        question.setApprovedAt(DUE);
        return questionRepository.save(question);
    }

    private MetisQuestion shown(MetisQuestion question, ZonedDateTime shownAt) {
        question.setShownAt(shownAt);
        return questionRepository.save(question);
    }

    private MetisQuestion answered(MetisQuestion question, ZonedDateTime answeredAt) {
        question.setShownAt(answeredAt.minusMinutes(1));
        question.setAnswerText("An answer");
        question.setAnsweredAt(answeredAt);
        return questionRepository.save(question);
    }

    private MetisQuestion rated(MetisQuestion question) {
        question.setLlmScore(3);
        question.setLlmRationale("Partly right");
        question.setLlmRatedAt(question.getAnsweredAt());
        return questionRepository.save(question);
    }

    private MetisQuestion reload(MetisQuestion question) {
        return questionRepository.findByIdElseThrow(question.getId());
    }
}
