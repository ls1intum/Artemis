import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faCheck, faCheckDouble, faPen, faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiButtonDirective,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiSelectComponent,
    TumAetUiTagComponent,
    TumAetUiTagSeverity,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { Observable, forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { MetisCheckStatus, MetisDashboardRowDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { FocusChipComponent } from 'app/metis/shared/focus-chip/focus-chip.component';
import { ReviewStatus, reviewStatusOf } from 'app/metis/shared/review-status.util';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/** The filter value that keeps every student. */
const ALL = 'ALL';

export type ReviewStatusFilter = 'ALL' | ReviewStatus;

/** One student card: the DTO plus the figures the card head shows, computed once per render. */
export interface ReviewStudentView {
    row: MetisDashboardRowDTO;
    /** The questions generated up front; follow-ups are generated live and are never reviewed. */
    mainQuestions: MetisQuestionDTO[];
    unapproved: MetisQuestionDTO[];
    status: ReviewStatus;
    files: number;
    changedLines: number;
}

interface SelectOption<T> {
    label: string;
    value: T;
}

const STATUS_FILTER_VALUES: ReviewStatusFilter[] = ['ALL', 'AWAITING', 'RELEASED', 'PREPARING', 'SKIPPED', 'FAILED'];

const REVIEW_STATUS_SEVERITY: Record<ReviewStatus, TumAetUiTagSeverity> = {
    PREPARING: 'info',
    AWAITING: 'warn',
    RELEASED: 'success',
    SKIPPED: 'secondary',
    FAILED: 'danger',
};

/** The check states in which the server still changes questions: generated, not started, and in review or released. */
const EDITABLE_CHECK_STATUSES: readonly MetisCheckStatus[] = [MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY];

const STATUS_FILTER_KEYS: Record<ReviewStatusFilter, string> = {
    ALL: 'artemisApp.metis.review.filters.allStatuses',
    AWAITING: 'artemisApp.metis.review.status.AWAITING',
    RELEASED: 'artemisApp.metis.review.status.RELEASED',
    PREPARING: 'artemisApp.metis.review.status.PREPARING',
    SKIPPED: 'artemisApp.metis.review.status.SKIPPED',
    FAILED: 'artemisApp.metis.review.status.FAILED',
};

function toStudentView(row: MetisDashboardRowDTO): ReviewStudentView {
    const mainQuestions = row.questions.filter((question) => question.parentQuestionId === undefined);
    return {
        row,
        mainQuestions,
        unapproved: mainQuestions.filter((question) => question.approvedAt === undefined),
        status: reviewStatusOf(row),
        files: new Set(mainQuestions.map((question) => question.focusFilePath).filter((file): file is string => !!file)).size,
        changedLines: mainQuestions.reduce((sum, question) => sum + (question.focusFileChangedLines ?? 0), 0),
    };
}

/**
 * The review gate of a comprehension check: every generated main question, per student, with the instructor's
 * three ways out of the gate: approve it as it is, change its text, or have the model generate it again.
 *
 * The page reads the exercise the route resolved and everything else through {@link MetisService}. An action patches
 * the question the server returns into its row instead of reloading the page. Approving everything and regenerating a
 * question load the rows again, because the server then moves checks between review and released; so does a refused
 * action, so a stale row never keeps offering what the server refuses.
 */
@Component({
    selector: 'jhi-metis-question-review',
    templateUrl: './metis-question-review.component.html',
    styleUrl: './metis-question-review.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleComponent,
        TumAetUiButtonDirective,
        TumAetUiTooltipDirective,
        TumAetUiTagComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
        TumAetUiInputDirective,
        FocusChipComponent,
    ],
})
export class MetisQuestionReviewComponent implements OnInit {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly metisService = inject(MetisService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    /** Resolved by the route; the page is only reachable with it. */
    protected readonly exercise: ProgrammingExercise = this.activatedRoute.snapshot.data['exercise'];
    protected readonly courseId = Number(this.activatedRoute.parent?.snapshot.paramMap.get('courseId'));

    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faCheck = faCheck;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faPen = faPen;
    protected readonly faSync = faSync;

    readonly rows = signal<MetisDashboardRowDTO[]>([]);
    readonly groupFilter = signal<string>(ALL);
    readonly statusFilter = signal<ReviewStatusFilter>(ALL);
    /** The question whose text is open in the editor, if any; only one is edited at a time. */
    readonly editingQuestionId = signal<number | undefined>(undefined);
    readonly editDraft = signal('');
    /** Questions the model is generating again right now; their buttons are disabled meanwhile. */
    readonly regeneratingIds = signal<ReadonlySet<number>>(new Set());

    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly studentViews = computed<ReviewStudentView[]>(() => this.rows().map((row) => toStudentView(row)));

    readonly counts = computed<Record<ReviewStatus, number>>(() => {
        const counts: Record<ReviewStatus, number> = { PREPARING: 0, AWAITING: 0, RELEASED: 0, SKIPPED: 0, FAILED: 0 };
        for (const view of this.studentViews()) {
            counts[view.status] += 1;
        }
        return counts;
    });

    protected readonly groupOptions = computed<SelectOption<string>[]>(() => {
        this.locale();
        const groups = [
            ...new Set(
                this.rows()
                    .map((row) => row.tutorialGroupTitle)
                    .filter((title): title is string => !!title),
            ),
        ].sort((a, b) => a.localeCompare(b));
        return [{ label: this.translateService.instant('artemisApp.metis.review.filters.allGroups'), value: ALL }, ...groups.map((group) => ({ label: group, value: group }))];
    });

    protected readonly statusOptions = computed<SelectOption<ReviewStatusFilter>[]>(() => {
        this.locale();
        return STATUS_FILTER_VALUES.map((status) => ({ label: this.translateService.instant(STATUS_FILTER_KEYS[status]), value: status }));
    });

    readonly visibleStudents = computed<ReviewStudentView[]>(() => {
        const group = this.groupFilter();
        const status = this.statusFilter();
        return this.studentViews()
            .filter((view) => group === ALL || view.row.tutorialGroupTitle === group)
            .filter((view) => status === ALL || view.status === status);
    });

    ngOnInit(): void {
        this.load();
    }

    /** Loads every row with its questions; also the way back to the server's state after approve-all, after a regeneration and after a refused action. */
    private load(): void {
        this.metisService
            .dashboard(this.exercise.id!)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((dashboard) => this.rows.set([...dashboard.rows]));
    }

    onGroupFilterChange(group: string): void {
        this.groupFilter.set(group);
    }

    onStatusFilterChange(status: ReviewStatusFilter): void {
        this.statusFilter.set(status);
    }

    statusSeverity(status: ReviewStatus): TumAetUiTagSeverity {
        return REVIEW_STATUS_SEVERITY[status];
    }

    /** The label of the regenerate button: it reads "Regenerating…" while the model works on that question. */
    regenerateLabelKey(questionId: number): string {
        return this.regeneratingIds().has(questionId) ? 'artemisApp.metis.review.regenerating' : 'artemisApp.metis.review.regenerate';
    }

    /**
     * Whether the server still accepts a new text or a regeneration for the question: nobody has seen it, and the check
     * of its student is generated but not started (in review or released). Everything else is answered with 409.
     * @param row the student the question belongs to
     * @param question the main question
     */
    isEditable(row: MetisDashboardRowDTO, question: MetisQuestionDTO): boolean {
        return !question.shownAt && EDITABLE_CHECK_STATUSES.includes(row.check.status);
    }

    /**
     * The failure reason of a skipped or failed check as a translation key, or undefined when the server named none.
     * @param row the student whose check was skipped or failed
     */
    failureReasonKey(row: MetisDashboardRowDTO): string | undefined {
        return row.check.failureReason ? `artemisApp.metis.failureReason.${row.check.failureReason}` : undefined;
    }

    approveQuestion(question: MetisQuestionDTO): void {
        this.patchOnSuccess(this.metisService.approveQuestion(this.exercise.id!, question.id), (approved) => this.patchQuestions([approved]));
    }

    /** Approves what is still open for one student; questions approved earlier are left alone. */
    approveStudent(view: ReviewStudentView): void {
        if (view.unapproved.length === 0) {
            return;
        }
        const approvals = view.unapproved.map((question) => this.metisService.approveQuestion(this.exercise.id!, question.id));
        this.patchOnSuccess(forkJoin(approvals), (approved) => this.patchQuestions(approved));
    }

    /** Approves every open main question of the exercise; the server releases the checks, so the page loads them once. */
    approveAllRemaining(): void {
        this.patchOnSuccess(this.metisService.approveAllQuestions(this.exercise.id!), () => this.load());
    }

    startEditing(question: MetisQuestionDTO): void {
        this.editingQuestionId.set(question.id);
        this.editDraft.set(question.questionText);
    }

    cancelEditing(): void {
        this.editingQuestionId.set(undefined);
    }

    saveText(question: MetisQuestionDTO): void {
        const text = this.editDraft().trim();
        if (!text) {
            return;
        }
        this.patchOnSuccess(this.metisService.updateQuestionText(this.exercise.id!, question.id, text), (updated) => {
            this.editingQuestionId.set(undefined);
            this.patchQuestions([updated]);
        });
    }

    /**
     * Asks the model for a new wording; the old text stays visible until the new one is there. The new wording is not
     * approved, so with a review the server returns the check to review, also one that an approval released while the
     * model was working. Only the server knows whether a review is required, so the page loads the rows again.
     */
    regenerate(question: MetisQuestionDTO): void {
        this.regeneratingIds.update((ids) => new Set([...ids, question.id]));
        const regeneration = this.metisService.regenerateQuestion(this.exercise.id!, question.id).pipe(
            finalize(() =>
                this.regeneratingIds.update((ids) => {
                    const next = new Set(ids);
                    next.delete(question.id);
                    return next;
                }),
            ),
        );
        this.patchOnSuccess(regeneration, (regenerated) => {
            this.patchQuestions([regenerated]);
            this.alertService.info('artemisApp.metis.review.regenerated', { count: regenerated.regenerationCount });
            this.load();
        });
    }

    /**
     * Runs a review action and applies its result. A refused action reloads the rows once: the global HTTP error
     * handling already shows the server's message, and the reload removes the buttons the server no longer accepts.
     */
    private patchOnSuccess<T>(action: Observable<T>, apply: (result: T) => void): void {
        action.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
            next: apply,
            error: () => this.load(),
        });
    }

    /** Replaces the questions the server returned in their rows; every other row keeps its identity. */
    private patchQuestions(questions: MetisQuestionDTO[]): void {
        const byId = new Map(questions.map((question) => [question.id, question]));
        this.rows.update((rows) =>
            rows.map((row) =>
                row.questions.some((question) => byId.has(question.id)) ? cloneWith(row, { questions: row.questions.map((question) => byId.get(question.id) ?? question) }) : row,
            ),
        );
    }
}
