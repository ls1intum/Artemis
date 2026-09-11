import {
    ChangeDetectorRef,
    Component,
    DestroyRef,
    ElementRef,
    afterNextRender,
    afterRenderEffect,
    computed,
    effect,
    inject,
    input,
    model,
    output,
    signal,
    viewChild,
} from '@angular/core';
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
import { QuizExerciseCountdownComponent } from 'app/exercise/exercise-headers/quiz-countdown/quiz-exercise-countdown.component';

/**
 * Width the title keeps for itself before the pills give way: the sidebar toggle plus enough of the exercise title to
 * still identify the exercise. The bar squeezes the title rather than growing, so without a floor the pills would push
 * the title down to an ellipsis and nothing would look wrong enough to notice.
 */
const MIN_TITLE_WIDTH_PX = 280;

/** The `gap-2` between the pills and the controls beside them, which the pills need on top of their own width. */
const PILLS_GAP_PX = 8;

/**
 * Element width in fractional CSS pixels, 0 for an element that is not there. `getBoundingClientRect` rather than
 * `offsetWidth` keeps the fraction, so the comparison stays exact at non-100% zoom.
 */
function widthOf(element: HTMLElement | undefined): number {
    return element ? element.getBoundingClientRect().width : 0;
}

/**
 * Whether a bar of `barWidth` can carry the pills next to its controls and still leave the title
 * {@link MIN_TITLE_WIDTH_PX}. A width of 0 means not measured yet — see {@link ExerciseHeaderComponent.showsPills} for
 * why that answers yes.
 */
export function pillsFitInTitleBar(barWidth: number, controlsWidth: number, pillsWidth: number): boolean {
    if (!barWidth || !pillsWidth) {
        return true;
    }
    return barWidth - controlsWidth - pillsWidth - PILLS_GAP_PX >= MIN_TITLE_WIDTH_PX;
}

@Component({
    selector: 'jhi-exercise-header',
    templateUrl: './exercise-header.component.html',
    imports: [
        FaIconComponent,
        ExerciseHeaderActionsComponent,
        ParticipationModeToggleComponent,
        CourseSidebarToggleButtonComponent,
        QuizExerciseCountdownComponent,
        ExerciseHeadersInformationComponent,
    ],
    styleUrl: './exercise-header.component.scss',
})
export class ExerciseHeaderComponent {
    protected readonly ExerciseType = ExerciseType;

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

    /**
     * Whether the bar is currently carrying the status and due date pills, so the details panel can leave them out
     * instead of repeating them. Reported rather than derived twice: only the bar knows how much room it has.
     */
    readonly showsPillsChange = output<boolean>();

    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly destroyRef = inject(DestroyRef);

    /** The bar itself, the controls that are always in it, and the pills — measured to decide whether the pills fit. */
    private readonly bar = viewChild<ElementRef<HTMLElement>>('bar');
    private readonly controls = viewChild<ElementRef<HTMLElement>>('controls');
    private readonly pills = viewChild<ElementRef<HTMLElement>>('pills');

    private readonly barWidth = signal(0);
    private readonly controlsWidth = signal(0);
    /**
     * Last measured width of the pills. Kept when they are not rendered, because that is exactly the width the fit
     * calculation needs in order to decide whether they could be shown again — a removed element has none to read.
     */
    private readonly pillsWidth = signal(0);

    /** Set once the view is gone: `detectChanges()` on a destroyed view throws, and an observer callback can still be queued. */
    private destroyed = false;

    /**
     * Whether the bar shows the pills itself. It does while the title still keeps {@link MIN_TITLE_WIDTH_PX} with them
     * in place; otherwise they give way and the details panel shows them instead.
     *
     * Before anything is measured the answer is yes. Starting hidden would mean never measuring them — a pill that is
     * not rendered has no width — so the first frame renders them and the next one takes them away again if they turn
     * out not to fit.
     */
    readonly showsPills = computed<boolean>(() => pillsFitInTitleBar(this.barWidth(), this.controlsWidth(), this.pillsWidth()));

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

    /**
     * Whether the student is looking at an earlier submission. Reported by the details panel, which now owns the
     * information boxes that know it; the header only reacts to it.
     */
    readonly isViewingSubmission = input<boolean>(false);

    /** Returns the student to their latest submission; supplied by the details panel for the same reason. */
    readonly onContinueToLatest = input<() => void>();

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
        return this.onContinueToLatest();
    });

    /**
     * Watches the pills for their width. Separate from the observer above because they come and go, so this one is
     * re-pointed at them whenever they reappear. A width of 0 is the element on its way out and is ignored, which is
     * what keeps the last known width — and stops a measurement from feeding back into the decision that removed it.
     */
    private readonly pillsObserver = new ResizeObserver(() => {
        if (this.destroyed) {
            return;
        }
        const width = widthOf(this.pills()?.nativeElement);
        if (width > 0 && width !== this.pillsWidth()) {
            this.pillsWidth.set(width);
            this.changeDetectorRef.detectChanges();
        }
    });

    /**
     * Watches the bar and the controls in it, both of which are there for the life of the view, so one observer covers
     * them. Change detection is flushed in the callback — which runs after layout and before paint — so a pill
     * appearing or giving way lands on the same frame rather than flickering for one.
     */
    protected readonly measureBarAndControls = afterNextRender(() => {
        const observer = new ResizeObserver(() => {
            if (this.destroyed) {
                return;
            }
            this.barWidth.set(widthOf(this.bar()?.nativeElement));
            this.controlsWidth.set(widthOf(this.controls()?.nativeElement));
            this.changeDetectorRef.detectChanges();
        });
        const barElement = this.bar()?.nativeElement;
        const controlsElement = this.controls()?.nativeElement;
        if (barElement) {
            observer.observe(barElement);
        }
        if (controlsElement) {
            observer.observe(controlsElement);
        }
        this.destroyRef.onDestroy(() => observer.disconnect());
    });

    /** Points {@link pillsObserver} at the pills for as long as they are rendered. */
    protected readonly followPillsWhileRendered = afterRenderEffect(() => {
        const pillsElement = this.pills()?.nativeElement;
        this.pillsObserver.disconnect();
        if (pillsElement) {
            this.pillsObserver.observe(pillsElement);
        }
    });

    /** Tells the page which of the two places is showing the pills, so the other one leaves them out. */
    protected readonly reportPillPlacement = effect(() => this.showsPillsChange.emit(this.showsPills()));

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.destroyed = true;
            this.pillsObserver.disconnect();
        });
    }

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
