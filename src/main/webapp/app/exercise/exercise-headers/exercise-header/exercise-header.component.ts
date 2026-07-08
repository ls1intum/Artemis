import { Component, computed, input, model, output, signal, viewChild } from '@angular/core';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseHeadersInformationComponent, QuizLiveHeaderInfo } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { ExerciseHeaderActionsComponent } from 'app/exercise/exercise-headers/exercise-header-actions/exercise-header-actions.component';
import { ParticipationMode, ParticipationModeToggleComponent } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT } from 'app/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { LiveQuizParticipationStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';

@Component({
    selector: 'jhi-exercise-header',
    templateUrl: './exercise-header.component.html',
    imports: [FaIconComponent, ExerciseHeadersInformationComponent, ExerciseHeaderActionsComponent, ParticipationModeToggleComponent, CourseSidebarToggleButtonComponent],
})
export class ExerciseHeaderComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly studentParticipation = input<StudentParticipation>();
    readonly practiceParticipation = input<StudentParticipation>();
    readonly submissionPolicy = input<SubmissionPolicy>();
    readonly onSubmitExercise = input<() => void>();
    readonly onRestartPractice = input<() => boolean>();
    readonly submitDisabled = input<boolean>(false);
    readonly submitLabel = input<string>('entity.action.submit');
    readonly plagiarismCaseInfo = input<PlagiarismCaseInfo>();
    readonly participationMode = model<ParticipationMode>('graded');
    readonly athenaEnabled = input<boolean>(false);
    readonly feedbackRequestLimit = input<number>(DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT);
    readonly quizLiveStatus = input<LiveQuizParticipationStatus>();
    readonly quizLiveHeaderInfo = input<QuizLiveHeaderInfo>();
    readonly showSidebarToggle = input<boolean>(false);
    readonly isSidebarCollapsed = input<boolean>(false);
    readonly newParticipation = output<StudentParticipation>();
    readonly toggleSidebar = output<void>();

    // Local signal to track a practice participation created in this session,
    // ensuring the toggle appears immediately without waiting for the parent round-trip.
    private readonly localPracticeParticipation = signal<StudentParticipation | undefined>(undefined);

    readonly exerciseIcon = computed(() => {
        const exercise = this.exercise();
        return exercise.type ? getIcon(exercise.type) : undefined;
    });

    readonly effectivePracticeParticipation = computed(() => {
        return this.practiceParticipation() ?? this.localPracticeParticipation();
    });

    // A TIMEOUT submission is collected automatically when the quiz ends without the student pressing
    // submit — the quiz evaluation marks it as submitted, but the student still missed the deadline, so
    // it must not produce a "Graded" badge.
    readonly hasGradedSubmission = computed(() => {
        return !!this.studentParticipation()?.submissions?.some((s) => s.submitted && s.type !== SubmissionType.TIMEOUT);
    });

    // A practice participation is only ever created by an explicit student action, so for any exercise
    // type its existence alone keeps the practice mode selectable — even without a submission (e.g. a
    // programming practice repository before the first push). The mode check additionally covers practice
    // runs whose participation has not been created yet (e.g. quiz practice just started).
    readonly showPracticeMode = computed(() => {
        return !!this.effectivePracticeParticipation() || this.participationMode() === 'practice';
    });

    // The graded side of the toggle is shown once a graded submission exists. While practice is available
    // it is always shown, so the student can switch back to the graded view — including the case where
    // the graded mode was missed entirely and the view explains the missed due date. Without practice, a
    // graded participation without submissions must not produce a (misleading) "Graded" badge.
    readonly showGradedMode = computed(() => {
        return this.hasGradedSubmission() || this.showPracticeMode();
    });

    readonly activeParticipation = computed(() => {
        return this.participationMode() === 'practice' ? this.effectivePracticeParticipation() : this.studentParticipation();
    });

    readonly isViewingSubmission = signal(false);

    private readonly headersInfo = viewChild(ExerciseHeadersInformationComponent);

    readonly effectiveOnSubmitExercise = computed(() => {
        if (this.isViewingSubmission()) {
            return undefined;
        }
        const exercise = this.exercise();
        const participation = this.activeParticipation();
        // Hide submit for graded participation after due date
        if (this.participationMode() === 'graded' && hasExerciseDueDatePassed(exercise, participation)) {
            return undefined;
        }
        // Hide submit for graded quiz after student has already submitted (practice allows multiple submissions)
        if (exercise.type === ExerciseType.QUIZ && this.participationMode() === 'graded' && participation?.submissions?.some((s) => s.submitted)) {
            return undefined;
        }
        return this.onSubmitExercise();
    });

    readonly onContinueExercise = computed(() => {
        if (!this.isViewingSubmission()) {
            return undefined;
        }
        return () => this.headersInfo()?.resultHistoryDropdown()?.continueToLatest();
    });

    onNewParticipation(participation: StudentParticipation) {
        if (participation.testRun) {
            this.localPracticeParticipation.set(participation);
        }

        this.newParticipation.emit(participation);
        if (participation.testRun) {
            this.participationMode.set('practice');
        }
    }
}
