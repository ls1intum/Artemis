import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import {
    TumAetUiButtonDirective,
    TumAetUiCardComponent,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiEmptyStateComponent,
    TumAetUiPanelComponent,
    TumAetUiProgressBarComponent,
    TumAetUiSelectComponent,
    TumAetUiTagComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleCheck, faCircleInfo, faGear, faPlay, faRotate, faStop, faTrash, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { EMPTY, Observable, Subject, Subscription, interval } from 'rxjs';
import { catchError, exhaustMap, finalize, tap, timeout } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { InfiniteScrollDirective } from 'app/shared-ui/infinite-scroll/infinite-scroll.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizQuestionPoolService } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.service';
import { QuizQuestionPoolItemListComponent } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool-item-list/quiz-question-pool-item-list.component';
import { QuizQuestionPoolSettingsDialogComponent } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool-settings-dialog/quiz-question-pool-settings-dialog.component';
import {
    PoolCompetency,
    PoolProgress,
    PoolQuestion,
    PoolQuestionPage,
    PoolReviewRequest,
    PoolSettings,
    PoolSettingsFact,
} from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

interface LectureUnitGroup {
    id: number;
    name: string;
    questions: PoolQuestion[];
}

interface CompetencyGroup {
    id: number;
    title: string;
    units: LectureUnitGroup[];
}

/** How many questions one request loads; the list grows by this many when its end scrolls into view or on "load more". */
const PAGE_SIZE = 20;
/** The most questions one refresh reloads while a build runs; the server caps requests at this size. */
const MAX_REFRESH_SIZE = 500;
/** How often progress and questions are reloaded while a build runs. */
const POLL_INTERVAL_MS = 3000;
/** How long a progress request may stay unanswered before the next poll tick replaces it. */
const POLL_TIMEOUT_MS = 10000;

const SETTINGS_KEY_PREFIX = 'artemisApp.hyperion.quizQuestionPool.settings.';

/**
 * The facts of the settings a build plans its questions with, in summary order; each is marked as changed when the reference settings
 * differ in it.
 */
function buildFactsOf(settings: PoolSettings, reference?: PoolSettings): PoolSettingsFact[] {
    const multipleChoice: PoolSettingsFact = settings.multipleChoiceEnabled
        ? {
              key: SETTINGS_KEY_PREFIX + (settings.multipleChoiceMinCorrectOptions === settings.multipleChoiceMaxCorrectOptions ? 'summaryMultipleOnFixed' : 'summaryMultipleOn'),
              values: {
                  count: settings.multipleChoiceQuestionsPerLectureUnit,
                  min: settings.multipleChoiceMinCorrectOptions,
                  max: settings.multipleChoiceMaxCorrectOptions,
              },
              changed:
                  !!reference &&
                  (!reference.multipleChoiceEnabled ||
                      reference.multipleChoiceQuestionsPerLectureUnit !== settings.multipleChoiceQuestionsPerLectureUnit ||
                      reference.multipleChoiceMinCorrectOptions !== settings.multipleChoiceMinCorrectOptions ||
                      reference.multipleChoiceMaxCorrectOptions !== settings.multipleChoiceMaxCorrectOptions),
          }
        : { key: SETTINGS_KEY_PREFIX + 'summaryMultipleOff', values: {}, changed: !!reference && reference.multipleChoiceEnabled };
    return [
        {
            key: SETTINGS_KEY_PREFIX + 'summarySingle',
            values: { count: settings.singleChoiceQuestionsPerLectureUnit },
            changed: !!reference && reference.singleChoiceQuestionsPerLectureUnit !== settings.singleChoiceQuestionsPerLectureUnit,
        },
        multipleChoice,
        {
            key: SETTINGS_KEY_PREFIX + 'summaryMix',
            values: { easy: settings.easyPercent, medium: settings.mediumPercent, hard: settings.hardPercent },
            changed:
                !!reference &&
                (reference.easyPercent !== settings.easyPercent || reference.mediumPercent !== settings.mediumPercent || reference.hardPercent !== settings.hardPercent),
        },
    ];
}

/**
 * The per-course page of Hyperion's quiz question pool: progress and build trigger, the course's pool settings, and the pool's
 * questions grouped by competency and lecture unit with the filter's verdicts and the review decision on each.
 */
@Component({
    selector: 'jhi-quiz-question-pool',
    templateUrl: './quiz-question-pool.component.html',
    imports: [
        FormsModule,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiCardComponent,
        TumAetUiConfirmDialogComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiPanelComponent,
        TumAetUiProgressBarComponent,
        TumAetUiSelectComponent,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        InfiniteScrollDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        QuizQuestionPoolItemListComponent,
        QuizQuestionPoolSettingsDialogComponent,
    ],
    providers: [TumAetUiConfirmationService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionPoolComponent implements OnInit, OnDestroy {
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly poolService = inject(QuizQuestionPoolService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly deleteDialogService = inject(DeleteDialogService);

    readonly faPlay = faPlay;
    readonly faRotate = faRotate;
    readonly faGear = faGear;
    readonly faTrash = faTrash;
    readonly faCircleCheck = faCircleCheck;
    readonly faCircleInfo = faCircleInfo;
    readonly faStop = faStop;

    readonly courseId = signal<number | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    readonly progress = signal<PoolProgress | undefined>(undefined);
    readonly questions = signal<PoolQuestion[]>([]);
    /** How many questions match the current competency filter across all pages. */
    readonly totalQuestionCount = signal(0);
    /** Flagged questions, undecided ones first, shown as a queue above the grouped list while no competency is selected. */
    readonly needsReviewQuestions = signal<PoolQuestion[]>([]);
    readonly needsReviewTotal = signal(0);
    /** Whether the queue is folded away; it folds on its own once every flagged question has a decision. */
    readonly queueCollapsed = signal(false);
    readonly competencyOptions = signal<PoolCompetency[]>([]);
    readonly isLoadingMore = signal(false);
    readonly isLoadingMoreNeedsReview = signal(false);
    readonly isDeleting = signal(false);
    /** Error text for the bulk-delete dialog; an empty value closes it. */
    private readonly deleteError = new Subject<string>();
    readonly settings = signal<PoolSettings | undefined>(undefined);
    readonly settingsDialogVisible = signal(false);
    readonly selectedCompetencyId = signal<number | undefined>(undefined);
    readonly isLoading = signal(false);
    readonly isStartingBuild = signal(false);
    /** Whether the running build was asked to stop and has not reported its end yet. */
    readonly isCancelling = signal(false);
    readonly isSavingSettings = signal(false);
    readonly busyQuestionIds = signal<ReadonlySet<number>>(new Set());

    private pollingSubscription?: Subscription;

    /** Advances whenever polling is requested; a final refresh stops polling only while no later request has been made. */
    private pollGeneration = 0;

    /** Orders question-list requests against stored review decisions. */
    private requestSequence = 0;

    /** The sequence number at which a question's review decision was last stored, by question id. */
    private readonly reviewSequences = new Map<number, number>();

    /** The sequence number of the question-list request whose response the list shows. */
    private appliedQuestionsAt = 0;

    readonly isInstructor = computed(() => !!this.course()?.isAtLeastInstructor);
    readonly canReview = computed(() => !!this.course()?.isAtLeastEditor);
    readonly canDelete = computed(() => !!this.course()?.isAtLeastInstructor);
    readonly isRunning = computed(() => !!this.progress()?.running);
    readonly progressPercent = computed(() => {
        const progress = this.progress();
        if (!progress || progress.total === 0) {
            return 0;
        }
        return Math.round(((progress.judged + progress.failed) / progress.total) * 100);
    });
    /** The settings the running build was planned with, while a build runs and they differ from the stored settings in what a build uses. */
    readonly runningBuildSettings = computed(() => {
        const build = this.progress()?.buildSettings;
        const stored = this.settings();
        return build && stored && this.isRunning() && buildFactsOf(stored, build).some((fact) => fact.changed) ? build : undefined;
    });
    readonly runningBuildFacts = computed(() => {
        const build = this.runningBuildSettings();
        return build ? buildFactsOf(build) : [];
    });
    /** The stored settings as the summary lists them, with the facts the running build differs in marked as changed. */
    readonly settingsFacts = computed<PoolSettingsFact[]>(() => {
        const stored = this.settings();
        if (!stored) {
            return [];
        }
        const overnight = { key: SETTINGS_KEY_PREFIX + (stored.poolGenerationEnabled ? 'summaryOvernightOn' : 'summaryOvernightOff'), values: {}, changed: false };
        return [...buildFactsOf(stored, this.runningBuildSettings()), overnight];
    });
    readonly remainingQuestions = computed(() => Math.max(0, this.totalQuestionCount() - this.questions().length));
    readonly remainingNeedsReview = computed(() => Math.max(0, this.needsReviewTotal() - this.needsReviewQuestions().length));
    readonly showNeedsReviewQueue = computed(() => this.selectedCompetencyId() === undefined && this.needsReviewQuestions().length > 0);
    readonly pendingInQueue = computed(() => this.needsReviewQuestions().filter((question) => question.reviewState === 'PENDING').length);
    readonly allFlaggedReviewed = computed(() => this.needsReviewQuestions().length > 0 && this.pendingInQueue() === 0);
    readonly groups = computed<CompetencyGroup[]>(() => {
        const groups = new Map<number, CompetencyGroup>();
        for (const question of this.questions()) {
            let group = groups.get(question.competencyId);
            if (!group) {
                group = { id: question.competencyId, title: question.competencyTitle, units: [] };
                groups.set(question.competencyId, group);
            }
            let unit = group.units.find((candidate) => candidate.id === question.lectureUnitId);
            if (!unit) {
                unit = { id: question.lectureUnitId, name: question.lectureUnitName, questions: [] };
                group.units.push(unit);
            }
            unit.questions.push(question);
        }
        return [...groups.values()];
    });
    ngOnInit(): void {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            const courseId = Number(params['courseId']);
            this.resetCourseState();
            this.courseId.set(courseId);
            this.loadCourse(courseId);
        });
    }

    /**
     * Drops the state of the course shown so far, including its polling, so that a course change never shows or refreshes the old one.
     */
    private resetCourseState(): void {
        this.stopPolling();
        this.course.set(undefined);
        this.progress.set(undefined);
        this.questions.set([]);
        this.totalQuestionCount.set(0);
        this.needsReviewQuestions.set([]);
        this.needsReviewTotal.set(0);
        this.queueCollapsed.set(false);
        this.competencyOptions.set([]);
        this.settings.set(undefined);
        this.settingsDialogVisible.set(false);
        this.selectedCompetencyId.set(undefined);
        this.busyQuestionIds.set(new Set());
        this.isCancelling.set(false);
        this.reviewSequences.clear();
        this.appliedQuestionsAt = 0;
    }

    private isCurrent(courseId: number): boolean {
        return this.courseId() === courseId;
    }

    /**
     * Replaces the question list with a server response, unless the response of a later request is shown already. Questions whose
     * review decision was stored after the request started keep their newer state until a later response carries it.
     */
    private applyQuestions(page: PoolQuestionPage, requestedAt: number): void {
        if (requestedAt < this.appliedQuestionsAt) {
            return;
        }
        this.appliedQuestionsAt = requestedAt;
        this.questions.update((current) => this.keepNewerDecisions(current, page.items, requestedAt));
        this.totalQuestionCount.set(page.totalCount);
        this.competencyOptions.set(page.competencies);
        const selected = this.selectedCompetencyId();
        const courseId = this.courseId();
        if (selected !== undefined && courseId && !page.competencies.some((competency) => competency.id === selected)) {
            this.selectedCompetencyId.set(undefined);
            this.loadPool(courseId);
        }
    }

    /** Takes the server's questions, except those whose review decision was stored after the request started. */
    private keepNewerDecisions(current: PoolQuestion[], incoming: PoolQuestion[], requestedAt: number): PoolQuestion[] {
        const byId = new Map(current.map((question) => [question.id, question]));
        return incoming.map((question) => ((this.reviewSequences.get(question.id) ?? 0) > requestedAt ? (byId.get(question.id) ?? question) : question));
    }

    /** The size that reloads every question shown so far in one request. */
    private refreshSize(loaded: number): number {
        return Math.min(MAX_REFRESH_SIZE, Math.max(PAGE_SIZE, Math.ceil(loaded / PAGE_SIZE) * PAGE_SIZE));
    }

    /**
     * Reloads the first {@code size} questions of the current competency filter and replaces the list with them.
     *
     * @returns completes after the list was replaced; a failed request completes without a value
     */
    private reloadQuestions(courseId: number, size: number): Observable<unknown> {
        const requestedAt = ++this.requestSequence;
        return this.poolService.getQuestions(courseId, { competencyId: this.selectedCompetencyId(), page: 0, size }).pipe(
            tap((page) => {
                if (this.isCurrent(courseId)) {
                    this.applyQuestions(page, requestedAt);
                }
            }),
        );
    }

    /**
     * Reloads the first {@code size} flagged questions, undecided ones first, or clears the queue while a competency is selected.
     * The queue folds when none of them awaits a decision.
     */
    private reloadNeedsReview(courseId: number, size: number): Observable<unknown> {
        if (this.selectedCompetencyId() !== undefined) {
            this.needsReviewQuestions.set([]);
            this.needsReviewTotal.set(0);
            return EMPTY;
        }
        const requestedAt = ++this.requestSequence;
        return this.poolService.getQuestions(courseId, { flagged: true, page: 0, size }).pipe(
            tap((page) => {
                if (this.isCurrent(courseId)) {
                    this.needsReviewQuestions.update((current) => this.keepNewerDecisions(current, page.items, requestedAt));
                    this.needsReviewTotal.set(page.totalCount);
                    this.queueCollapsed.set(this.allFlaggedReviewed());
                }
            }),
        );
    }

    /** Appends the next page of the grouped list. */
    loadMoreQuestions(): void {
        const courseId = this.courseId();
        if (!courseId || this.isLoadingMore() || this.remainingQuestions() === 0) {
            return;
        }
        this.isLoadingMore.set(true);
        const requestedAt = ++this.requestSequence;
        const page = Math.floor(this.questions().length / PAGE_SIZE);
        this.poolService
            .getQuestions(courseId, { competencyId: this.selectedCompetencyId(), page, size: PAGE_SIZE })
            .pipe(
                finalize(() => this.isLoadingMore.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (result) => {
                    if (this.isCurrent(courseId) && requestedAt > this.appliedQuestionsAt) {
                        const known = new Set(this.questions().map((question) => question.id));
                        this.questions.update((current) => [...current, ...result.items.filter((question) => !known.has(question.id))]);
                        this.totalQuestionCount.set(result.totalCount);
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
    }

    /** Appends the next page of the review queue. */
    loadMoreNeedsReview(): void {
        const courseId = this.courseId();
        if (!courseId || this.isLoadingMoreNeedsReview() || this.remainingNeedsReview() === 0) {
            return;
        }
        this.isLoadingMoreNeedsReview.set(true);
        const page = Math.floor(this.needsReviewQuestions().length / PAGE_SIZE);
        this.poolService
            .getQuestions(courseId, { flagged: true, page, size: PAGE_SIZE })
            .pipe(
                finalize(() => this.isLoadingMoreNeedsReview.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (result) => {
                    if (this.isCurrent(courseId)) {
                        const known = new Set(this.needsReviewQuestions().map((question) => question.id));
                        this.needsReviewQuestions.update((current) => [...current, ...result.items.filter((question) => !known.has(question.id))]);
                        this.needsReviewTotal.set(result.totalCount);
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
    }

    ngOnDestroy(): void {
        this.stopPolling();
    }

    private loadCourse(courseId: number): void {
        this.courseManagementService
            .find(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    this.course.set(response.body ?? undefined);
                    this.loadPool(courseId);
                    if (response.body?.isAtLeastInstructor) {
                        this.loadSettings(courseId);
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
    }

    /**
     * Reloads progress and questions, and keeps polling while a build is running.
     */
    loadPool(courseId: number): void {
        this.isLoading.set(true);
        this.loadProgress(courseId);
        this.reloadQuestions(courseId, PAGE_SIZE)
            .pipe(
                finalize(() => this.isLoading.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({ error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed') });
        this.reloadNeedsReview(courseId, PAGE_SIZE)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed') });
    }

    private loadProgress(courseId: number): void {
        this.poolService
            .getProgress(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (progress) => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    this.applyProgress(progress);
                    if (progress.running) {
                        this.startPolling(courseId);
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
    }

    /** Takes a progress response; a build that has stopped ends the cancelling state. */
    private applyProgress(progress: PoolProgress): void {
        this.progress.set(progress);
        if (!progress.running) {
            this.isCancelling.set(false);
        }
    }

    private loadSettings(courseId: number): void {
        this.poolService
            .getSettings(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (settings) => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    this.settings.set(settings);
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
    }

    startBuild(): void {
        const courseId = this.courseId();
        if (!courseId || this.isStartingBuild()) {
            return;
        }
        this.isStartingBuild.set(true);
        this.poolService
            .startBuild(courseId)
            .pipe(
                finalize(() => this.isStartingBuild.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (result) => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    if (result.started) {
                        this.alertService.success('artemisApp.hyperion.quizQuestionPool.progress.started', { count: result.enqueued });
                    } else {
                        this.alertService.warning('artemisApp.hyperion.quizQuestionPool.progress.alreadyRunning');
                    }
                    this.startPolling(courseId);
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.progress.buildFailed'),
            });
    }

    /** Asks for confirmation, then stops the running build after its current step. */
    confirmCancelBuild(): void {
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.progress.cancelConfirmHeader'),
            message: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.progress.cancelConfirmMessage'),
            icon: faTriangleExclamation,
            acceptLabel: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.progress.cancelConfirmAccept'),
            acceptSeverity: 'danger',
            rejectLabel: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.progress.cancelConfirmReject'),
            rejectSeverity: 'secondary',
            accept: () => this.cancelBuild(),
        });
    }

    private cancelBuild(): void {
        const courseId = this.courseId();
        if (!courseId || this.isCancelling()) {
            return;
        }
        this.isCancelling.set(true);
        this.poolService
            .cancelBuild(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    if (this.isCurrent(courseId)) {
                        this.alertService.success('artemisApp.hyperion.quizQuestionPool.progress.cancelRequested');
                        this.startPolling(courseId);
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.isCancelling.set(false);
                    if (error.status === 409) {
                        this.alertService.warning('artemisApp.hyperion.quizQuestionPool.progress.cancelNotRunning');
                        this.loadProgress(courseId);
                    } else {
                        this.alertService.error('artemisApp.hyperion.quizQuestionPool.progress.cancelFailed');
                    }
                },
            });
    }

    refresh(): void {
        const courseId = this.courseId();
        if (courseId) {
            this.loadPool(courseId);
        }
    }

    openSettingsDialog(): void {
        if (this.settings()) {
            this.settingsDialogVisible.set(true);
        }
    }

    /**
     * Stores the settings the dialog hands over and closes it on success; on failure the dialog stays open with its draft. While a
     * build runs, the instructor first confirms that the change reaches the next build only.
     *
     * @param settings the edited settings
     */
    saveSettings(settings: PoolSettings): void {
        if (!this.isRunning()) {
            this.storeSettings(settings);
            return;
        }
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.settings.nextBuildHeader'),
            message: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.settings.nextBuildMessage'),
            icon: faCircleInfo,
            acceptLabel: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.settings.nextBuildAccept'),
            rejectLabel: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.settings.cancel'),
            rejectSeverity: 'secondary',
            accept: () => this.storeSettings(settings),
        });
    }

    private storeSettings(settings: PoolSettings): void {
        const courseId = this.courseId();
        if (!courseId || this.isSavingSettings()) {
            return;
        }
        this.isSavingSettings.set(true);
        this.poolService
            .updateSettings(courseId, settings)
            .pipe(
                finalize(() => this.isSavingSettings.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (stored) => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    this.settings.set(stored);
                    this.settingsDialogVisible.set(false);
                    this.alertService.success('artemisApp.hyperion.quizQuestionPool.settings.saved');
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.settings.saveFailed'),
            });
    }

    selectCompetency(competencyId: number | undefined): void {
        this.selectedCompetencyId.set(competencyId);
        const courseId = this.courseId();
        if (courseId) {
            this.loadPool(courseId);
        }
    }

    review(request: PoolReviewRequest): void {
        const courseId = this.courseId();
        if (!courseId || this.busyQuestionIds().has(request.questionId)) {
            return;
        }
        this.busyQuestionIds.update((ids) => new Set([...ids, request.questionId]));
        this.poolService
            .review(courseId, request.questionId, request.reviewState)
            .pipe(
                finalize(() =>
                    this.busyQuestionIds.update((ids) => {
                        const next = new Set(ids);
                        next.delete(request.questionId);
                        return next;
                    }),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (updated) => {
                    if (this.isCurrent(courseId)) {
                        this.reviewSequences.set(updated.id, ++this.requestSequence);
                        this.questions.update((questions) => questions.map((question) => (question.id === updated.id ? updated : question)));
                        this.needsReviewQuestions.update((queue) => queue.map((question) => (question.id === updated.id ? updated : question)));
                        if (this.allFlaggedReviewed()) {
                            this.queueCollapsed.set(true);
                        }
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.list.reviewFailed'),
            });
    }

    /** Asks for confirmation, then removes one question from the pool. */
    confirmDeleteQuestion(questionId: number): void {
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.list.deleteConfirmHeader'),
            message: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.list.deleteConfirmMessage'),
            icon: faTriangleExclamation,
            acceptLabel: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.list.delete'),
            acceptSeverity: 'danger',
            rejectLabel: this.translateService.instant('artemisApp.hyperion.quizQuestionPool.settings.cancel'),
            rejectSeverity: 'secondary',
            accept: () => this.deleteQuestion(questionId),
        });
    }

    private deleteQuestion(questionId: number): void {
        const courseId = this.courseId();
        if (!courseId || this.busyQuestionIds().has(questionId)) {
            return;
        }
        this.busyQuestionIds.update((ids) => new Set([...ids, questionId]));
        this.poolService
            .deleteQuestion(courseId, questionId)
            .pipe(
                finalize(() =>
                    this.busyQuestionIds.update((ids) => {
                        const next = new Set(ids);
                        next.delete(questionId);
                        return next;
                    }),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: () => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    if (this.questions().some((question) => question.id === questionId)) {
                        this.questions.update((questions) => questions.filter((question) => question.id !== questionId));
                        this.totalQuestionCount.update((total) => Math.max(0, total - 1));
                    }
                    if (this.needsReviewQuestions().some((question) => question.id === questionId)) {
                        this.needsReviewQuestions.update((queue) => queue.filter((question) => question.id !== questionId));
                        this.needsReviewTotal.update((total) => Math.max(0, total - 1));
                    }
                    this.alertService.success('artemisApp.hyperion.quizQuestionPool.list.deleted');
                    this.loadProgress(courseId);
                },
                error: (error: HttpErrorResponse) => this.alertService.error(this.deleteErrorKey(error, 'artemisApp.hyperion.quizQuestionPool.list.deleteFailed')),
            });
    }

    /**
     * Opens the delete dialog for every question of one competency, or of the whole pool, and requires the course title.
     *
     * @param competencyId the competency to clear, or undefined for the whole pool
     */
    confirmDeleteQuestions(competencyId?: number): void {
        const course = this.course();
        if (!course) {
            return;
        }
        const competency = competencyId === undefined ? undefined : this.competencyOptions().find((option) => option.id === competencyId);
        this.deleteDialogService.openDeleteDialog({
            entityTitle: course.title ?? '',
            deleteQuestion: competency ? 'artemisApp.hyperion.quizQuestionPool.deleteDialog.questionCompetency' : 'artemisApp.hyperion.quizQuestionPool.deleteDialog.questionAll',
            deleteConfirmationText: 'artemisApp.hyperion.quizQuestionPool.deleteDialog.typeCourseTitle',
            translateValues: competency ? { competency: competency.title } : { count: this.progress()?.total ?? 0 },
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            requireConfirmationOnlyForAdditionalChecks: false,
            dialogError: this.deleteError.asObservable(),
            delete: () => this.deleteQuestions(competencyId),
        });
    }

    private deleteQuestions(competencyId?: number): void {
        const courseId = this.courseId();
        if (!courseId || this.isDeleting()) {
            return;
        }
        this.isDeleting.set(true);
        this.poolService
            .deleteQuestions(courseId, competencyId)
            .pipe(
                finalize(() => this.isDeleting.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (deleted) => {
                    this.deleteError.next('');
                    if (this.isCurrent(courseId)) {
                        this.alertService.success('artemisApp.hyperion.quizQuestionPool.deleteDialog.deleted', { count: deleted });
                        if (competencyId === undefined || competencyId === this.selectedCompetencyId()) {
                            this.selectedCompetencyId.set(undefined);
                        }
                        this.loadPool(courseId);
                    }
                },
                error: (error: HttpErrorResponse) =>
                    this.deleteError.next(this.translateService.instant(this.deleteErrorKey(error, 'artemisApp.hyperion.quizQuestionPool.deleteDialog.failed'))),
            });
    }

    /** A conflict means a build is running; every other failure gets the given text. */
    private deleteErrorKey(error: HttpErrorResponse, fallbackKey: string): string {
        return error.status === 409 ? 'artemisApp.hyperion.quizQuestionPool.errors.buildRunning' : fallbackKey;
    }

    /**
     * Polls progress and questions until a progress response reports no running build and the question list requested on it has
     * arrived; a failed question request leaves polling on, so the next tick asks again. A request to poll made while that question
     * list is pending, such as a new build, keeps polling on.
     */
    private startPolling(courseId: number): void {
        this.pollGeneration++;
        if (this.pollingSubscription) {
            return;
        }
        this.pollingSubscription = interval(POLL_INTERVAL_MS)
            .pipe(
                exhaustMap(() =>
                    this.poolService.getProgress(courseId).pipe(
                        timeout(POLL_TIMEOUT_MS),
                        catchError(() => EMPTY),
                    ),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (progress) => {
                    if (!this.isCurrent(courseId)) {
                        this.stopPolling();
                        return;
                    }
                    this.applyProgress(progress);
                    const generation = this.pollGeneration;
                    this.reloadNeedsReview(courseId, this.refreshSize(this.needsReviewQuestions().length))
                        .pipe(
                            catchError(() => EMPTY),
                            takeUntilDestroyed(this.destroyRef),
                        )
                        .subscribe();
                    this.reloadQuestions(courseId, this.refreshSize(this.questions().length))
                        .pipe(
                            catchError(() => EMPTY),
                            takeUntilDestroyed(this.destroyRef),
                        )
                        .subscribe(() => {
                            if (this.isCurrent(courseId) && !progress.running && generation === this.pollGeneration) {
                                this.stopPolling();
                            }
                        });
                },
            });
    }

    private stopPolling(): void {
        this.pollingSubscription?.unsubscribe();
        this.pollingSubscription = undefined;
    }
}
