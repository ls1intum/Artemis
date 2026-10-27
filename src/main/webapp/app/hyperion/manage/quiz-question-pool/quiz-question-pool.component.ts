import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faExclamationTriangle, faPlay, faRotate } from '@fortawesome/free-solid-svg-icons';
import { Subscription, interval } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { ProgressBarModule } from 'primeng/progressbar';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { AlertService } from 'app/foundation/service/alert.service';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizQuestionPoolService } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.service';
import { PoolReviewRequest, QuizQuestionPoolItemListComponent } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool-item-list/quiz-question-pool-item-list.component';
import { PoolProgress, PoolQuestion, PoolSettings } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

export interface CompetencyOption {
    id: number;
    title: string;
}

export interface LectureUnitGroup {
    id: number;
    name: string;
    questions: PoolQuestion[];
}

export interface CompetencyGroup {
    id: number;
    title: string;
    units: LectureUnitGroup[];
}

/** How often progress and questions are reloaded while a build runs. */
const POLL_INTERVAL_MS = 3000;

/** Bounds of the questions-per-lecture-unit setting, matching the server's validation. */
export const MIN_QUESTIONS_PER_LECTURE_UNIT = 1;
export const MAX_QUESTIONS_PER_LECTURE_UNIT = 60;

/**
 * The per-course page of Hyperion's quiz question pool: progress and build trigger, the course's pool settings, and the pool's
 * questions grouped by competency and lecture unit with the filter's verdicts and the review decision on each.
 */
@Component({
    selector: 'jhi-quiz-question-pool',
    templateUrl: './quiz-question-pool.component.html',
    styleUrl: './quiz-question-pool.component.scss',
    imports: [
        FormsModule,
        FaIconComponent,
        ButtonModule,
        InputNumberModule,
        ProgressBarModule,
        SelectModule,
        TagModule,
        ToggleSwitchModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        QuizQuestionPoolItemListComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionPoolComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private destroyRef = inject(DestroyRef);
    private courseManagementService = inject(CourseManagementService);
    private poolService = inject(QuizQuestionPoolService);
    private alertService = inject(AlertService);

    readonly faPlay = faPlay;
    readonly faRotate = faRotate;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly minQuestionsPerLectureUnit = MIN_QUESTIONS_PER_LECTURE_UNIT;
    readonly maxQuestionsPerLectureUnit = MAX_QUESTIONS_PER_LECTURE_UNIT;

    readonly courseId = signal<number | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    readonly progress = signal<PoolProgress | undefined>(undefined);
    readonly questions = signal<PoolQuestion[]>([]);
    readonly settings = signal<PoolSettings | undefined>(undefined);
    private readonly originalSettings = signal<PoolSettings | undefined>(undefined);
    readonly selectedCompetencyId = signal<number | undefined>(undefined);
    readonly isLoading = signal(false);
    readonly isStartingBuild = signal(false);
    readonly isSavingSettings = signal(false);
    readonly busyQuestionIds = signal<ReadonlySet<number>>(new Set());

    private pollingSubscription?: Subscription;

    readonly isInstructor = computed(() => !!this.course()?.isAtLeastInstructor);
    readonly canReview = computed(() => !!this.course()?.isAtLeastEditor);
    readonly isRunning = computed(() => !!this.progress()?.running);
    readonly progressPercent = computed(() => {
        const progress = this.progress();
        if (!progress || progress.total === 0) {
            return 0;
        }
        return Math.round(((progress.judged + progress.failed) / progress.total) * 100);
    });
    readonly competencyOptions = computed<CompetencyOption[]>(() => {
        const options = new Map<number, CompetencyOption>();
        for (const question of this.questions()) {
            if (!options.has(question.competencyId)) {
                options.set(question.competencyId, { id: question.competencyId, title: question.competencyTitle });
            }
        }
        return [...options.values()];
    });
    readonly groups = computed<CompetencyGroup[]>(() => {
        const selected = this.selectedCompetencyId();
        const groups = new Map<number, CompetencyGroup>();
        for (const question of this.questions()) {
            if (selected !== undefined && question.competencyId !== selected) {
                continue;
            }
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
    readonly isSettingsDirty = computed(() => {
        const settings = this.settings();
        const original = this.originalSettings();
        if (!settings || !original) {
            return false;
        }
        return settings.poolGenerationEnabled !== original.poolGenerationEnabled || settings.questionsPerLectureUnit !== original.questionsPerLectureUnit;
    });
    readonly isSettingsValid = computed(() => {
        const questions = this.settings()?.questionsPerLectureUnit;
        return questions !== undefined && questions >= MIN_QUESTIONS_PER_LECTURE_UNIT && questions <= MAX_QUESTIONS_PER_LECTURE_UNIT;
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
        this.settings.set(undefined);
        this.originalSettings.set(undefined);
        this.selectedCompetencyId.set(undefined);
        this.busyQuestionIds.set(new Set());
    }

    private isCurrent(courseId: number): boolean {
        return this.courseId() === courseId;
    }

    ngOnDestroy(): void {
        this.stopPolling();
    }

    canDeactivate(): boolean {
        return !this.isSettingsDirty();
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
        this.poolService
            .getProgress(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (progress) => {
                    if (!this.isCurrent(courseId)) {
                        return;
                    }
                    this.progress.set(progress);
                    if (progress.running) {
                        this.startPolling(courseId);
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
        this.poolService
            .getQuestions(courseId)
            .pipe(
                finalize(() => this.isLoading.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (questions) => {
                    if (this.isCurrent(courseId)) {
                        this.questions.set(questions);
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.errors.loadFailed'),
            });
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
                    this.settings.set(deepClone(settings));
                    this.originalSettings.set(deepClone(settings));
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

    refresh(): void {
        const courseId = this.courseId();
        if (courseId) {
            this.loadPool(courseId);
        }
    }

    saveSettings(): void {
        const courseId = this.courseId();
        const settings = this.settings();
        if (!courseId || !settings || !this.isSettingsValid() || this.isSavingSettings()) {
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
                    this.settings.set(deepClone(stored));
                    this.originalSettings.set(deepClone(stored));
                    this.alertService.success('artemisApp.hyperion.quizQuestionPool.settings.saved');
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.settings.saveFailed'),
            });
    }

    setPoolGenerationEnabled(enabled: boolean): void {
        this.settings.update((settings) => (settings ? cloneWith(settings, { poolGenerationEnabled: enabled }) : settings));
    }

    setQuestionsPerLectureUnit(questions: number | undefined): void {
        this.settings.update((settings) => (settings ? cloneWith(settings, { questionsPerLectureUnit: questions ?? 0 }) : settings));
    }

    selectCompetency(competencyId: number | undefined | null): void {
        this.selectedCompetencyId.set(competencyId ?? undefined);
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
                        this.questions.update((questions) => questions.map((question) => (question.id === updated.id ? updated : question)));
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.quizQuestionPool.list.reviewFailed'),
            });
    }

    private startPolling(courseId: number): void {
        if (this.pollingSubscription) {
            return;
        }
        this.pollingSubscription = interval(POLL_INTERVAL_MS)
            .pipe(
                switchMap(() => this.poolService.getProgress(courseId)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (progress) => {
                    if (!this.isCurrent(courseId)) {
                        this.stopPolling();
                        return;
                    }
                    this.progress.set(progress);
                    this.poolService
                        .getQuestions(courseId)
                        .pipe(takeUntilDestroyed(this.destroyRef))
                        .subscribe((questions) => {
                            if (this.isCurrent(courseId)) {
                                this.questions.set(questions);
                            }
                        });
                    if (!progress.running) {
                        this.stopPolling();
                    }
                },
                error: () => this.stopPolling(),
            });
    }

    private stopPolling(): void {
        this.pollingSubscription?.unsubscribe();
        this.pollingSubscription = undefined;
    }
}
