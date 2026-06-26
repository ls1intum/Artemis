import {
    ChangeDetectorRef,
    Component,
    DestroyRef,
    ElementRef,
    afterNextRender,
    afterRenderEffect,
    computed,
    inject,
    input,
    output,
    signal,
    viewChild,
    viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { RouterLink } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import {
    faBoxesStacked,
    faChartBar,
    faClipboardList,
    faEllipsis,
    faEye,
    faLightbulb,
    faListAlt,
    faPencilAlt,
    faPlayCircle,
    faPlus,
    faRedo,
    faStopCircle,
    faTable,
    faTrash,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { Popover, PopoverModule } from 'primeng/popover';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { Exercise, ExerciseMode, ExerciseType, getExerciseUrlSegment } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { Course } from 'app/course/shared/entities/course.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';

/**
 * A single exercise action. `quiz` group items are the quiz lifecycle buttons: they always stay visible. `main` group
 * items render inline and collapse into the ellipsis menu (from the left, so Delete stays longest) when the action
 * area is too narrow to show them all.
 */
interface ActionItem {
    id: string;
    /** i18n key for the button label, resolved via the `artemisTranslate` pipe. */
    labelKey: string;
    /** Optional interpolation params for `labelKey` (e.g. the batch count). */
    labelArgs?: { [key: string]: unknown };
    icon: IconProp;
    styleClass: string;
    group: 'quiz' | 'main';
    kind: 'link' | 'button' | 'delete' | 'batches';
    link?: (string | number)[];
    onClick?: () => void;
}

// Flex gap (gap-1 = 0.25rem) between items, the fixed ellipsis-trigger width (see SCSS `.action-more`), and a small
// safety margin so a button is always collapsed slightly before it would be clipped — never shown partially.
const GAP_PX = 4;
const ELLIPSIS_WIDTH_PX = 40;
const SAFETY_MARGIN_PX = 8;

@Component({
    selector: 'jhi-exercise-actions',
    templateUrl: './exercise-actions.component.html',
    styleUrl: './exercise-actions.component.scss',
    imports: [RouterLink, NgTemplateOutlet, FaIconComponent, PopoverModule, ArtemisTranslatePipe, TranslateDirective, DeleteButtonDirective, QuizExerciseLifecycleButtonsComponent],
})
export class ExerciseActionsComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);

    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();

    protected readonly ExerciseType = ExerciseType;
    protected readonly faEllipsis = faEllipsis;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faPlus = faPlus;

    private readonly destroyRef = inject(DestroyRef);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly textExerciseService = inject(TextExerciseService);
    private readonly fileUploadExerciseService = inject(FileUploadExerciseService);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly eventManager = inject(EventManager);
    private readonly translateService = inject(TranslateService);

    private readonly menu = viewChild<Popover>('menu');
    private readonly batchMenu = viewChild<Popover>('batchMenu');
    private readonly quizLifecycle = viewChild(QuizExerciseLifecycleButtonsComponent);
    /** The full-width action row; its width minus the quiz buttons is the budget for the collapsible main buttons. */
    private readonly actionsRow = viewChild<ElementRef<HTMLElement>>('actionsRow');
    /** The always-visible quiz lifecycle buttons; their width is reserved up front. */
    private readonly quizGroup = viewChild<ElementRef<HTMLElement>>('quizGroup');
    /** The inline main-button wrappers, read once per distinct button to learn its natural width. */
    private readonly inlineItems = viewChildren<ElementRef<HTMLElement>>('inlineItem');

    /** Full row width (updated by a ResizeObserver) and the reserved width of the always-visible quiz buttons. */
    private readonly rowWidth = signal(0);
    private readonly quizWidth = signal(0);
    /** Width available for the collapsible main buttons: the row minus the quiz buttons. */
    private readonly availableWidth = computed<number>(() => this.rowWidth() - this.quizWidth());
    /** Natural width per button, keyed by a signature (id + label) so changing labels (e.g. `Batches (n)`) re-measure. */
    private readonly buttonWidths = signal<ReadonlyMap<string, number>>(new Map());
    /** Bumped on a language change so the (signal-unaware) translated measurements recompute. */
    private readonly languageVersion = signal(0);

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    /** The current exercise typed as a quiz, or `undefined` for non-quiz exercises. Drives the lifecycle buttons. */
    readonly quizExercise = computed<QuizExercise | undefined>(() => {
        const ex = this.exercise();
        return ex.type === ExerciseType.QUIZ ? (ex as QuizExercise) : undefined;
    });

    /** Batches of the current quiz (empty for non-batched / non-quiz exercises). Shown in the batches popover. */
    readonly quizBatches = computed<QuizBatch[]>(() => this.quizExercise()?.quizBatches ?? []);

    /** Whether the batches trigger and popover apply: a running or visible batched quiz. */
    readonly showBatchMenu = computed<boolean>(() => {
        const quiz = this.quizExercise();
        return !!quiz && quiz.quizMode === QuizMode.BATCHED && (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE);
    });

    /**
     * Quiz lifecycle buttons styled like the rest of the action row. Visibility mirrors the develop lifecycle buttons
     * (see {@link QuizExerciseLifecycleButtonsComponent}); clicking delegates to that hidden component's public methods,
     * which own the backend calls and optimistic state updates.
     */
    readonly quizActions = computed<ActionItem[]>(() => {
        const quiz = this.quizExercise();
        if (!quiz) return [];
        const items: ActionItem[] = [];
        // Make visible: an invisible quiz students cannot see yet.
        if (quiz.status === QuizStatus.INVISIBLE && quiz.isAtLeastEditor && !quiz.visibleToStudents) {
            items.push({
                id: 'set-visible',
                labelKey: 'artemisApp.quizExercise.showNow',
                icon: faEye,
                styleClass: 'btn-warning',
                group: 'quiz',
                kind: 'button',
                onClick: () => this.quizLifecycle()?.showQuiz(),
            });
        }
        // Start: a synchronized quiz that has not been started yet.
        if ((quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.INVISIBLE) && quiz.quizMode === QuizMode.SYNCHRONIZED && quiz.isAtLeastEditor && !quiz.quizStarted) {
            items.push({
                id: 'start',
                labelKey: 'artemisApp.quizExercise.startQuiz',
                icon: faPlayCircle,
                styleClass: 'btn-success',
                group: 'quiz',
                kind: 'button',
                onClick: () => this.quizLifecycle()?.startQuiz(),
            });
        }
        // Batched: a single trigger that opens a popover listing each batch (id, password, status) and adds new ones.
        if (this.showBatchMenu()) {
            items.push({
                id: 'batches',
                labelKey: 'artemisApp.quizExercise.batches',
                labelArgs: { count: quiz.quizBatches?.length ?? 0 },
                icon: faBoxesStacked,
                styleClass: 'btn-primary',
                group: 'quiz',
                kind: 'batches',
            });
        }
        // End: a running non-synchronized quiz that has not ended yet (synchronized quizzes end via their duration).
        if ((quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE) && quiz.quizMode !== QuizMode.SYNCHRONIZED && quiz.isAtLeastInstructor && !quiz.quizEnded) {
            items.push({
                id: 'end',
                labelKey: 'artemisApp.quizExercise.endQuiz',
                icon: faStopCircle,
                styleClass: 'btn-danger',
                group: 'quiz',
                kind: 'button',
                onClick: () => this.quizLifecycle()?.endQuiz(),
            });
        }
        return items;
    });

    /** Regular actions in original display order: Teams → Participations → Scores → type-specific → Edit → Delete. */
    readonly mainActions = computed<ActionItem[]>(() => {
        const ex = this.exercise();
        const cid = this.courseId();
        const seg = getExerciseUrlSegment(ex.type);
        const items: ActionItem[] = [];

        if (ex.mode === ExerciseMode.TEAM) {
            items.push({
                id: 'teams',
                labelKey: 'artemisApp.exercise.teams',
                icon: faUsers,
                styleClass: 'btn-primary',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, 'exercises', ex.id!, 'teams'],
            });
        }
        items.push({
            id: 'participations',
            labelKey: 'artemisApp.exercise.participations',
            icon: faListAlt,
            styleClass: 'btn-primary',
            group: 'main',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'participations'],
        });
        items.push({
            id: 'scores',
            labelKey: 'entity.action.scores',
            icon: faTable,
            styleClass: 'btn-info',
            group: 'main',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'scores'],
        });
        if (ex.type === ExerciseType.QUIZ) {
            const q = ex as QuizExercise;
            items.push({
                id: 'statistics',
                labelKey: 'artemisApp.quizExercise.statistics',
                icon: faChartBar,
                styleClass: 'btn-info',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'quiz-point-statistic'],
            });
            items.push({
                id: 'preview',
                labelKey: 'artemisApp.quizExercise.preview',
                icon: faEye,
                styleClass: 'btn-success',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'preview'],
            });
            items.push({
                id: 'solution',
                labelKey: 'artemisApp.quizExercise.solution',
                icon: faLightbulb,
                styleClass: 'btn-success',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'solution'],
            });
            if (q.quizEnded) {
                items.push({
                    id: 're-evaluate',
                    labelKey: 'entity.action.re-evaluate',
                    icon: faRedo,
                    styleClass: 'btn-warning',
                    group: 'main',
                    kind: 'link',
                    link: ['/course-management', cid, seg, ex.id!, 're-evaluate'],
                });
            }
        }
        if (ex.type === ExerciseType.MODELING || ex.type === ExerciseType.TEXT) {
            items.push({
                id: 'examples',
                labelKey: 'entity.action.exampleSubmissions',
                icon: faClipboardList,
                styleClass: 'btn-success',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'example-submissions'],
            });
        }
        if (ex.type === ExerciseType.PROGRAMMING) {
            items.push({
                id: 'edit-in-editor',
                labelKey: 'entity.action.editInEditor',
                icon: faPencilAlt,
                styleClass: 'btn-warning',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, 'programming-exercises', ex.id!, 'code-editor', RepositoryType.TEMPLATE, -1],
            });
        }
        items.push({
            id: 'edit',
            labelKey: 'entity.action.edit',
            icon: faWrench,
            styleClass: 'btn-warning',
            group: 'main',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'edit'],
        });
        items.push({ id: 'delete', labelKey: 'entity.action.delete', icon: faTrash, styleClass: 'btn-danger', group: 'main', kind: 'delete' });
        return items;
    });

    /** Signature that determines a button's rendered width: same signature ⇒ same width. Uses the translated label so a
     * language switch or a changed batch count re-measures. */
    protected signatureOf(action: ActionItem): string {
        return `${action.id}|${this.translateService.instant(action.labelKey, action.labelArgs)}`;
    }

    /**
     * Ids of the main actions that do not fit and are collapsed into the ellipsis menu. Computed from the cached button
     * widths and the available width — the DOM stays stable, only the buttons' `display` toggles, so resizing never
     * recreates button elements. Collapses from the left so Delete (rightmost) stays visible longest.
     */
    readonly hiddenIds = computed<ReadonlySet<string>>(() => {
        const actions = this.mainActions();
        const widths = this.buttonWidths();
        const available = this.availableWidth() - SAFETY_MARGIN_PX;
        // Not yet measured (or sized): show everything until widths/width are known.
        if (available <= 0 || actions.some((action) => !widths.has(this.signatureOf(action)))) {
            return new Set();
        }
        const widthOf = (action: ActionItem) => widths.get(this.signatureOf(action)) ?? 0;
        // N buttons laid out with N-1 gaps between them.
        const widthForCount = (count: number) => (count <= 0 ? 0 : (count - 1) * GAP_PX);

        // Everything fits inline: no ellipsis, no hiding.
        const totalAll = actions.reduce((sum, action) => sum + widthOf(action), 0) + widthForCount(actions.length);
        if (totalAll <= available) {
            return new Set();
        }

        // Collapsing: reserve the ellipsis (plus the gap before it) and fill kept buttons from the right (Delete last).
        const budget = available - ELLIPSIS_WIDTH_PX - GAP_PX;
        let used = 0;
        let keepFrom = actions.length;
        for (let i = actions.length - 1; i >= 0; i--) {
            const addition = widthOf(actions[i]) + (keepFrom < actions.length ? GAP_PX : 0);
            if (used + addition <= budget) {
                used += addition;
                keepFrom = i;
            } else {
                break;
            }
        }
        return new Set(actions.slice(0, keepFrom).map((action) => action.id));
    });

    readonly hasOverflow = computed<boolean>(() => this.hiddenIds().size > 0);

    /** The collapsed actions, shown in the ellipsis menu. */
    readonly hiddenActions = computed<ActionItem[]>(() => this.mainActions().filter((action) => this.hiddenIds().has(action.id)));

    readonly deletionSummary = computed<Observable<EntitySummary>>(() => this.exerciseService.getDeletionSummary(this.exercise()));

    /** Placeholders for the delete confirmation question (`{{ courseType }}` / `{{ courseTitle }}`). */
    readonly deleteTranslateValues = computed<{ [key: string]: unknown }>(() => {
        const course = this.course();
        return {
            courseTitle: course?.title,
            courseType: this.translateService.instant(course?.testCourse ? 'artemisApp.exercise.delete.testCourse' : 'artemisApp.exercise.delete.realCourse'),
        };
    });

    constructor() {
        // Translated labels have different widths per language; the measurements use TranslateService.instant (not a
        // signal), so on a language change drop the cached widths to re-measure and bump the version the quiz-width
        // effect watches. The buttons themselves update via the (impure) artemisTranslate pipe.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.buttonWidths.set(new Map());
            this.languageVersion.update((version) => version + 1);
        });

        // Track the full row width: one cheap clientWidth read per resize tick, no per-button measuring. Flush change
        // detection synchronously in the observer callback (which runs after layout but before paint) so the show/hide
        // lands on the same frame — otherwise the toggle waits for the async scheduler and lags a frame behind a zoom.
        afterNextRender(() => {
            const rowEl = this.actionsRow()?.nativeElement;
            if (!rowEl) {
                return;
            }
            const observer = new ResizeObserver(() => {
                this.rowWidth.set(rowEl.clientWidth);
                this.changeDetectorRef.detectChanges();
            });
            observer.observe(rowEl);
            this.destroyRef.onDestroy(() => observer.disconnect());
            this.rowWidth.set(rowEl.clientWidth);
        });

        // Measure the always-visible quiz buttons' width whenever the quiz action set or language changes (they never
        // collapse, so their reserved width just needs to stay accurate).
        afterRenderEffect(() => {
            this.quizActions();
            this.languageVersion();
            const quizEl = this.quizGroup()?.nativeElement;
            if (quizEl) {
                this.quizWidth.set(quizEl.offsetWidth);
            }
        });

        // Measure each distinct button's natural width once (while it is visible) and cache it. Hidden buttons read 0,
        // so we never overwrite a cached width; warm rows skip the measurement entirely.
        afterRenderEffect(() => {
            const actions = this.mainActions();
            const current = this.buttonWidths();
            if (actions.every((action) => current.has(this.signatureOf(action)))) {
                return;
            }
            let next: Map<string, number> | undefined;
            for (const ref of this.inlineItems()) {
                const el = ref.nativeElement;
                const signature = el.getAttribute('data-signature');
                if (signature && el.offsetWidth > 0 && !current.has(signature)) {
                    next ??= new Map(current);
                    next.set(signature, el.offsetWidth);
                }
            }
            if (next) {
                this.buttonWidths.set(next);
            }
        });
    }

    protected toggleMenu(event: Event): void {
        this.menu()?.toggle(event);
    }

    protected runAction(item: ActionItem): void {
        item.onClick?.();
        this.menu()?.hide();
    }

    protected closeMenuIfOpen(inMenu: boolean): void {
        if (inMenu) {
            this.menu()?.hide();
        }
    }

    protected toggleBatchMenu(event: Event): void {
        this.batchMenu()?.toggle(event);
    }

    /** Add a new batch via the hidden lifecycle component (which performs the backend call and returns its password). */
    protected addBatch(): void {
        this.quizLifecycle()?.addBatch();
    }

    /** Start the given batch via the hidden lifecycle component. */
    protected startBatch(batchId: number): void {
        this.quizLifecycle()?.startBatch(batchId);
    }

    /**
     * Relays an optimistic quiz update from the lifecycle component to the parent. The client-derived `status` /
     * `quizStarted` flags are recomputed first (as the develop quiz view does) so the action buttons reflect the new
     * state immediately.
     */
    protected onQuizLifecycleUpdate(quiz: QuizExercise): void {
        quiz.status = this.quizExerciseService.getStatus(quiz);
        quiz.quizStarted = quiz.status === QuizStatus.ACTIVE;
        this.exerciseUpdated.emit(quiz);
    }

    /**
     * The lifecycle component emits `loadOne` to reload a quiz from the server (e.g. after a failed mutation reverts the
     * optimistic state). The experimental view holds the exercise locally, so we just re-emit the current value to force
     * the parent to refresh that row.
     */
    protected onQuizReload(): void {
        this.exerciseUpdated.emit(this.exercise());
    }

    protected onDelete(event: { [key: string]: boolean }): void {
        const exercise = this.exercise();
        const finish = (obs: Observable<unknown>, evtName: string) =>
            obs.subscribe({
                next: () => {
                    this.eventManager.broadcast({ name: evtName, content: 'Deleted an exercise' });
                    this.dialogErrorSource.next('');
                    // Notify the parent so the deleted exercise is removed from the view without a page refresh.
                    this.exerciseDeleted.emit(exercise);
                },
                error: (e: HttpErrorResponse) => this.dialogErrorSource.next(e.message),
            });

        switch (exercise.type) {
            case ExerciseType.TEXT:
                finish(this.textExerciseService.delete(exercise.id!), 'textExerciseListModification');
                break;
            case ExerciseType.FILE_UPLOAD:
                finish(this.fileUploadExerciseService.delete(exercise.id!), 'fileUploadExerciseListModification');
                break;
            case ExerciseType.QUIZ:
                finish(this.quizExerciseService.delete(exercise.id!), 'quizExerciseListModification');
                break;
            case ExerciseType.MODELING:
                finish(this.modelingExerciseService.delete(exercise.id!), 'modelingExerciseListModification');
                break;
            case ExerciseType.PROGRAMMING:
                finish(
                    this.programmingExerciseService.delete(exercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans),
                    'programmingExerciseListModification',
                );
                break;
        }
    }
}
