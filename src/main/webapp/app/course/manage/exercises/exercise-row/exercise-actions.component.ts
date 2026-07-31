import {
    ChangeDetectionStrategy,
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
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
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
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';
import { TumUiPopoverComponent } from 'app/shared-ui/tum-ui/popover/tum-ui-popover.component';
import { TumUiPopoverTriggerDirective } from 'app/shared-ui/tum-ui/popover/tum-ui-popover-trigger.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
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
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

/** The kit button severities the action buttons use (a subset of `TumUiButtonSeverity`). */
type ActionSeverity = 'primary' | 'info' | 'success' | 'warn' | 'danger';

/** A single collapsible main action rendered in the action row or the ellipsis overflow menu. */
interface ActionItem {
    id: string;
    /** i18n key for the button label, resolved via the `artemisTranslate` pipe. */
    labelKey: string;
    icon: IconProp;
    severity: ActionSeverity;
    kind: 'link' | 'button' | 'delete';
    link?: (string | number)[];
    onClick?: () => void;
    /** When true the action is rendered greyed-out and non-interactive (links become non-navigable, buttons disabled). */
    disabled?: boolean;
    /** i18n key for the tooltip shown on a disabled action. */
    disabledTooltip?: string;
}

// Flex gap (gap-1), the fixed ellipsis-trigger width (SCSS `.action-more`), and a margin so a button collapses
// slightly before it would be clipped.
const GAP_PX = 4;
const ELLIPSIS_WIDTH_PX = 40;
const SAFETY_MARGIN_PX = 8;

/**
 * Keep-priority per action id when the row runs out of width: lower stays inline longer. Delete, Edit and Scores
 * rank highest so every exercise type keeps the same three inline; the type-specific extras overflow first.
 */
const ACTION_KEEP_PRIORITY: Readonly<Record<string, number>> = { delete: 0, edit: 1, scores: 2 };
const EXTRA_ACTION_PRIORITY = 3;

function keepPriorityOf(action: ActionItem): number {
    return ACTION_KEEP_PRIORITY[action.id] ?? EXTRA_ACTION_PRIORITY;
}

/** Element width in fractional CSS pixels — `offsetWidth` rounds, which understates a sum at non-100% zoom. */
function widthOf(element: HTMLElement): number {
    return element.getBoundingClientRect().width;
}

@Component({
    selector: 'jhi-exercise-actions',
    templateUrl: './exercise-actions.component.html',
    styleUrl: './exercise-actions.component.scss',
    imports: [
        RouterLink,
        NgTemplateOutlet,
        FaIconComponent,
        TumUiButtonDirective,
        TumUiPopoverComponent,
        TumUiPopoverTriggerDirective,
        TumUiTooltipDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
        DeleteButtonDirective,
        QuizExerciseLifecycleButtonsComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseActionsComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);

    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();
    /**
     * Width (px) the actions column must reserve for this row's quiz buttons plus the ellipsis trigger; 0 for
     * non-quiz rows. The table floors the shared column at the max across its rows (see exercise-table).
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
    private readonly profileService = inject(ProfileService);
    private readonly featureToggleService = inject(FeatureToggleService);

    private readonly localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    /**
     * Whether programming exercises are enabled server-side; defaults to active until the toggle resolves. Actions
     * are data, not markup, so this folds into `ActionItem.disabled` instead of a `jhiFeatureToggle` directive.
     */
    private readonly programmingEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.ProgrammingExercises), { initialValue: true });

    private readonly menu = viewChild<TumUiPopoverComponent>('menu');
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

    /** Watches the inline buttons so {@link buttonWidths} tracks their real rendered width (see the constructor). */
    private readonly buttonObserver = new ResizeObserver((entries) => this.onButtonsResized(entries));

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    /** The current exercise typed as a quiz, or `undefined` for non-quiz exercises. Drives the lifecycle buttons. */
    readonly quizExercise = computed<QuizExercise | undefined>(() => {
        const ex = this.exercise();
        return ex.type === ExerciseType.QUIZ ? ex : undefined;
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
                severity: 'primary',
                kind: 'link',
                link: ['/course-management', cid, 'exercises', ex.id!, 'teams'],
            });
        }
        items.push({
            id: 'participations',
            labelKey: 'artemisApp.exercise.participations',
            icon: faListAlt,
            severity: 'primary',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'participations'],
        });
        items.push({
            id: 'scores',
            labelKey: 'entity.action.scores',
            icon: faTable,
            severity: 'info',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'scores'],
        });
        if (ex.type === ExerciseType.QUIZ) {
            const q = ex as QuizExercise;
            items.push({
                id: 'statistics',
                labelKey: 'artemisApp.quizExercise.statistics',
                icon: faChartBar,
                severity: 'info',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'quiz-point-statistic'],
            });
            items.push({
                id: 'preview',
                labelKey: 'artemisApp.quizExercise.preview',
                icon: faEye,
                severity: 'success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'preview'],
            });
            items.push({
                id: 'solution',
                labelKey: 'artemisApp.quizExercise.solution',
                icon: faLightbulb,
                severity: 'success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'solution'],
            });
            if (q.quizEnded && ex.isAtLeastInstructor) {
                items.push({
                    id: 're-evaluate',
                    labelKey: 'entity.action.re-evaluate',
                    icon: faRedo,
                    severity: 'warn',
                    kind: 'link',
                    link: ['/course-management', cid, seg, ex.id!, 're-evaluate'],
                });
            }
        }
        // Both example-submission routes are IS_AT_LEAST_EDITOR, so tutors would only hit an access denial.
        if (ex.isAtLeastEditor && (ex.type === ExerciseType.MODELING || ex.type === ExerciseType.TEXT)) {
            items.push({
                id: 'examples',
                labelKey: 'entity.action.exampleSubmissions',
                icon: faClipboardList,
                severity: 'success',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'example-submissions'],
            });
        }
        // Programming-only actions stay visible but go inert while the feature toggle is off.
        const programmingDisabled = ex.type === ExerciseType.PROGRAMMING && !this.programmingEnabled();
        // Editing requires editor rights, so tutors must not see the edit controls.
        if (ex.type === ExerciseType.PROGRAMMING && ex.isAtLeastEditor) {
            items.push({
                id: 'edit-in-editor',
                labelKey: 'entity.action.editInEditor',
                icon: faPencilAlt,
                severity: 'warn',
                kind: 'link',
                link: ['/course-management', cid, 'programming-exercises', ex.id!, 'code-editor', RepositoryType.TEMPLATE, -1],
                disabled: programmingDisabled || undefined,
                disabledTooltip: programmingDisabled ? 'artemisApp.exerciseManagement.programmingFeatureDisabled' : undefined,
            });
        }
        if (ex.isAtLeastEditor) {
            if (ex.type !== ExerciseType.QUIZ) {
                items.push({
                    id: 'edit',
                    labelKey: 'entity.action.edit',
                    icon: faWrench,
                    severity: 'warn',
                    kind: 'link',
                    link: ['/course-management', cid, seg, ex.id!, 'edit'],
                });
            } else {
                const q2 = ex as QuizExercise;
                // Prefer the server-supplied isEditable (set by loadQuizBatches); until it arrives, check client-side.
                const editable = q2.isEditable !== false && (q2.isEditable === true || isQuizEditable(q2));
                const editDisabled = !editable || !!q2.quizEnded;
                items.push({
                    id: 'edit',
                    labelKey: 'entity.action.edit',
                    icon: faWrench,
                    severity: 'warn',
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
        }
        if (ex.isAtLeastInstructor) {
            items.push({
                id: 'delete',
                labelKey: 'entity.action.delete',
                icon: faTrash,
                severity: 'danger',
                kind: 'delete',
                disabled: programmingDisabled || undefined,
                disabledTooltip: programmingDisabled ? 'artemisApp.exerciseManagement.programmingFeatureDisabled' : undefined,
            });
        }
        return items;
    });

    /** Signature that determines a button's rendered width: same signature ⇒ same width. Uses the translated label so a
     * language switch or a changed batch count re-measures. */
    protected signatureOf(action: ActionItem): string {
        return `${action.id}|${this.translateService.instant(action.labelKey)}`;
    }

    /**
     * Ids of the main actions that do not fit and are collapsed into the ellipsis menu. Derived from the cached
     * button widths, so resizing only toggles their `display` and never recreates elements.
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

        // Collapsing: reserve the ellipsis plus its gap, then keep buttons in priority order (not display order, so
        // every exercise type keeps the same core buttons inline) until one no longer fits.
        const budget = available - ELLIPSIS_WIDTH_PX - GAP_PX;
        const byPriority = [...actions].sort((a, b) => keepPriorityOf(a) - keepPriorityOf(b));
        const keptIds = new Set<string>();
        let used = 0;
        for (const action of byPriority) {
            const addition = widthOf(action) + (keptIds.size > 0 ? GAP_PX : 0);
            if (used + addition <= budget) {
                used += addition;
                keptIds.add(action.id);
            } else {
                break;
            }
        }
        return new Set(actions.filter((action) => !keptIds.has(action.id)).map((action) => action.id));
    });

    /**
     * The inline main buttons with their render state precomputed, so the template reads plain fields instead of
     * calling helpers per cycle. `context` is built here too, to keep the `ngTemplateOutlet` binding identity stable.
     */
    protected readonly inlineActions = computed(() => {
        // The signature embeds a translated label, so re-derive it on a language switch.
        this.languageVersion();
        const hidden = this.hiddenIds();
        return this.mainActions().map((action) => ({
            action,
            signature: this.signatureOf(action),
            hidden: hidden.has(action.id),
            context: { $implicit: action, inMenu: false },
        }));
    });

    readonly hasOverflow = computed<boolean>(() => this.hiddenIds().size > 0);

    /** The collapsed actions, shown in the ellipsis menu. */
    readonly hiddenActions = computed<ActionItem[]>(() => this.mainActions().filter((action) => this.hiddenIds().has(action.id)));

    readonly deletionSummary = computed<Observable<EntitySummary>>(() => this.exerciseService.getDeletionSummary(this.exercise()));

    /**
     * Cleanup checkboxes for the delete dialog: on external CI, programming exercises offer an opt-out for deleting
     * repositories and build plans. Hidden under LocalCI, where the request omits the flags and the server decides.
     */
    readonly deleteAdditionalChecks = computed((): { [key: string]: string } => {
        if (this.exercise().type !== ExerciseType.PROGRAMMING || this.localCIEnabled) {
            return {};
        }
        return {
            deleteStudentReposBuildPlans: 'artemisApp.programmingExercise.delete.studentReposBuildPlans',
            deleteBaseReposBuildPlans: 'artemisApp.programmingExercise.delete.baseReposBuildPlans',
        };
    });

    /** Placeholders for the delete confirmation question (`{{ courseType }}` / `{{ courseTitle }}`). */
    readonly deleteTranslateValues = computed<{ [key: string]: unknown }>(() => {
        const course = this.course();
        return {
            courseTitle: course?.title,
            courseType: this.translateService.instant(course?.testCourse ? 'artemisApp.exercise.delete.testCourse' : 'artemisApp.exercise.delete.realCourse'),
        };
    });

    constructor() {
        this.destroyRef.onDestroy(() => this.buttonObserver.disconnect());

        // Measurements use TranslateService.instant (not a signal), so on a language change drop the cached widths to
        // re-measure and bump the version the width effects watch.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.buttonWidths.set(new Map());
            this.languageVersion.update((version) => version + 1);
        });

        // Observe rather than measure once: the quiz buttons' PrimeNG CSS is injected lazily, so a single read can
        // catch them unstyled. Change detection is flushed in the callback so the show/hide lands on the same frame.
        afterNextRender(() => {
            const rowEl = this.actionsRow()?.nativeElement;
            const quizEl = this.quizGroup()?.nativeElement;
            const measure = () => {
                if (rowEl) {
                    this.rowWidth.set(widthOf(rowEl));
                }
                this.quizWidth.set(quizEl ? widthOf(quizEl) : 0);
            };

            const observer = new ResizeObserver(() => {
                measure();
                this.changeDetectorRef.detectChanges();
            });
            if (rowEl) {
                observer.observe(rowEl);
            }
            if (quizEl) {
                observer.observe(quizEl);
            }
            this.destroyRef.onDestroy(() => observer.disconnect());
            measure();
        });

        // Keep each button's natural width up to date: a web font or a longer label can change it after the first
        // layout, and a cached too-narrow width would keep a button inline that then gets clipped.
        afterRenderEffect(() => {
            // Re-observe whenever the buttons or their labels change; the initial callback seeds new signatures
            // after a language switch. The elements persist across a collapse, so this does not re-run on resize.
            this.languageVersion();
            this.mainActions();
            const items = this.inlineItems();
            this.buttonObserver.disconnect();
            for (const ref of items) {
                this.buttonObserver.observe(ref.nativeElement);
            }
        });

        // Report the width this row's actions column must reserve: the quiz buttons (measured width includes the
        // trailing separator), the gap, the ellipsis trigger and a safety margin. Non-quiz rows report 0.
        effect(() => {
            const quizWidth = this.quizWidth();
            this.quizActionsMinWidth.emit(quizWidth > 0 ? quizWidth + GAP_PX + ELLIPSIS_WIDTH_PX + SAFETY_MARGIN_PX : 0);
        });
    }

    /**
     * Caches the measured width of every laid-out button. Collapsed buttons measure 0, so their last known width is
     * kept — that is what decides whether they fit again. Writes only on a real change, so the toggling settles.
     */
    private onButtonsResized(entries: ResizeObserverEntry[]): void {
        const current = this.buttonWidths();
        let next: Map<string, number> | undefined;
        for (const entry of entries) {
            const element = entry.target as HTMLElement;
            const signature = element.getAttribute('data-signature');
            const width = widthOf(element);
            if (signature && width > 0 && current.get(signature) !== width) {
                next ??= new Map(current);
                next.set(signature, width);
            }
        }
        if (next) {
            this.buttonWidths.set(next);
            // Land the show/hide on this frame (the callback runs after layout, before paint), as the row observer does.
            this.changeDetectorRef.detectChanges();
        }
    }

    protected runAction(item: ActionItem): void {
        item.onClick?.();
        this.menu()?.close();
    }

    protected closeMenuIfOpen(inMenu: boolean): void {
        if (inMenu) {
            this.menu()?.close();
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
     * Re-fetches the quiz when the lifecycle component asks for a reload (e.g. after a failed mutation reverted the
     * optimistic state), since the stale local copy would keep offering an action the server just rejected.
     */
    protected onQuizReload(): void {
        const exerciseId = this.exercise().id;
        if (exerciseId === undefined) {
            return;
        }
        this.quizExerciseService
            .find(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    const quiz = response.body;
                    if (quiz) {
                        quiz.status = this.quizExerciseService.getStatus(quiz);
                        quiz.quizStarted = quiz.status === QuizStatus.ACTIVE;
                        this.exerciseUpdated.emit(quiz);
                    }
                },
                error: (e: HttpErrorResponse) => this.dialogErrorSource.next(e.message),
            });
    }

    protected onDelete(event: { [key: string]: boolean }): void {
        const exercise = this.exercise();
        const exerciseId = exercise.id;
        if (exerciseId === undefined) {
            return;
        }
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
                finish(this.textExerciseService.delete(exerciseId), 'textExerciseListModification');
                break;
            case ExerciseType.FILE_UPLOAD:
                finish(this.fileUploadExerciseService.delete(exerciseId), 'fileUploadExerciseListModification');
                break;
            case ExerciseType.QUIZ:
                finish(this.quizExerciseService.delete(exerciseId), 'quizExerciseListModification');
                break;
            case ExerciseType.MODELING:
                finish(this.modelingExerciseService.delete(exerciseId), 'modelingExerciseListModification');
                break;
            case ExerciseType.PROGRAMMING:
                finish(
                    this.programmingExerciseService.delete(exerciseId, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans),
                    'programmingExerciseListModification',
                );
                break;
        }
    }
}
