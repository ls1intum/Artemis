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
    faChartBar,
    faClipboardList,
    faEllipsis,
    faEye,
    faLightbulb,
    faListAlt,
    faPencilAlt,
    faRedo,
    faTable,
    faTrash,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { Popover, PopoverModule } from 'primeng/popover';
import { TooltipModule } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { Exercise, ExerciseMode, ExerciseType, getExerciseUrlSegment } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseLifecycleButtonsComponent } from 'app/quiz/manage/lifecyle-buttons/quiz-exercise-lifecycle-buttons.component';
import { isQuizEditable } from 'app/quiz/shared/service/quiz-manage-util.service';
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

/** A single collapsible main action rendered in the action row or the ellipsis overflow menu. */
interface ActionItem {
    id: string;
    /** i18n key for the button label, resolved via the `artemisTranslate` pipe. */
    labelKey: string;
    icon: IconProp;
    styleClass: string;
    kind: 'link' | 'button' | 'delete';
    link?: (string | number)[];
    onClick?: () => void;
    /** When true the link is rendered greyed-out and non-navigable. */
    disabled?: boolean;
    /** i18n key for the tooltip shown on a disabled link. */
    disabledTooltip?: string;
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
    imports: [RouterLink, NgTemplateOutlet, FaIconComponent, PopoverModule, TooltipModule, ArtemisTranslatePipe, DeleteButtonDirective, QuizExerciseLifecycleButtonsComponent],
})
export class ExerciseActionsComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);

    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();
    /**
     * Width (px) the actions column must reserve to keep this row's always-visible quiz lifecycle buttons plus the
     * ellipsis trigger on screen — i.e. the point at which every collapsible main button has folded into the ellipsis.
     * 0 for non-quiz rows. The table floors the shared column at the max reported across its rows (see exercise-table),
     * so the column shrinks (collapsing the main buttons) before the table scrolls, never clipping the quiz buttons.
     */
    readonly quizActionsMinWidth = output<number>();

    protected readonly ExerciseType = ExerciseType;
    protected readonly faEllipsis = faEllipsis;

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

    /** True when the lifecycle buttons component will render at least one button. Used to show/hide the separator. */
    readonly hasQuizButtons = computed<boolean>(() => {
        const quiz = this.quizExercise();
        if (!quiz) return false;
        const showVisible = quiz.status === QuizStatus.INVISIBLE && !!quiz.isAtLeastEditor && !quiz.visibleToStudents;
        const showStart =
            (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.INVISIBLE) && quiz.quizMode === QuizMode.SYNCHRONIZED && !!quiz.isAtLeastEditor && !quiz.quizStarted;
        const showBatches = quiz.quizMode === QuizMode.BATCHED && (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE);
        const showEnd =
            (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE) && quiz.quizMode !== QuizMode.SYNCHRONIZED && !!quiz.isAtLeastInstructor && !quiz.quizEnded;
        return showVisible || showStart || showBatches || showEnd;
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
                kind: 'link',
                link: ['/course-management', cid, 'exercises', ex.id!, 'teams'],
            });
        }
        items.push({
            id: 'participations',
            labelKey: 'artemisApp.exercise.participations',
            icon: faListAlt,
            styleClass: 'btn-primary',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'participations'],
        });
        items.push({
            id: 'scores',
            labelKey: 'entity.action.scores',
            icon: faTable,
            styleClass: 'btn-info',
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
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'quiz-point-statistic'],
            });
            items.push({
                id: 'preview',
                labelKey: 'artemisApp.quizExercise.preview',
                icon: faEye,
                styleClass: 'btn-success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'preview'],
            });
            items.push({
                id: 'solution',
                labelKey: 'artemisApp.quizExercise.solution',
                icon: faLightbulb,
                styleClass: 'btn-success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'solution'],
            });
            if (q.quizEnded) {
                items.push({
                    id: 're-evaluate',
                    labelKey: 'entity.action.re-evaluate',
                    icon: faRedo,
                    styleClass: 'btn-warning',
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
                kind: 'link',
                link: ['/course-management', cid, 'programming-exercises', ex.id!, 'code-editor', RepositoryType.TEMPLATE, -1],
            });
        }
        if (ex.type !== ExerciseType.QUIZ) {
            items.push({
                id: 'edit',
                labelKey: 'entity.action.edit',
                icon: faWrench,
                styleClass: 'btn-warning',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'edit'],
            });
        } else {
            const q2 = ex as QuizExercise;
            // Use server-supplied isEditable when available (set by loadQuizBatches); fall back to client check
            // for the brief window before batches load. isEditable: undefined → fallback, false → not editable, true → editable.
            const editable = q2.isEditable !== false && (q2.isEditable === true || isQuizEditable(q2));
            const editDisabled = !editable || !!q2.quizEnded;
            items.push({
                id: 'edit',
                labelKey: 'entity.action.edit',
                icon: faWrench,
                styleClass: 'btn-warning',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'edit'],
                disabled: editDisabled || undefined,
                disabledTooltip: q2.quizEnded
                    ? 'artemisApp.quizExercise.edit.editNotPossibleAfterEnd'
                    : !editable && q2.status === QuizStatus.ACTIVE
                      ? 'artemisApp.quizExercise.editNotPossibleDuringQuiz'
                      : !editable
                        ? 'artemisApp.quizExercise.editNotPossibleStudentsStarted'
                        : undefined,
            });
        }
        items.push({ id: 'delete', labelKey: 'entity.action.delete', icon: faTrash, styleClass: 'btn-danger', kind: 'delete' });
        return items;
    });

    /** Signature that determines a button's rendered width: same signature ⇒ same width. Uses the translated label so a
     * language switch or a changed batch count re-measures. */
    protected signatureOf(action: ActionItem): string {
        return `${action.id}|${this.translateService.instant(action.labelKey)}`;
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

        // Measure the always-visible quiz buttons' width whenever the quiz state or language changes (they never
        // collapse, so their reserved width just needs to stay accurate).
        afterRenderEffect(() => {
            this.quizExercise();
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

        // Report the width this row's actions column must reserve: the always-visible quiz buttons (the measured
        // quizGroup already includes their trailing separator) plus the gap, the ellipsis trigger, and a small safety
        // margin so sub-pixel rounding never clips the left edge of the leftmost quiz button. Quiz-button widths do not
        // depend on the column width, so this never feeds back into its own measurement. Non-quiz rows report 0.
        effect(() => {
            const quizWidth = this.quizWidth();
            this.quizActionsMinWidth.emit(quizWidth > 0 ? quizWidth + GAP_PX + ELLIPSIS_WIDTH_PX + SAFETY_MARGIN_PX : 0);
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
