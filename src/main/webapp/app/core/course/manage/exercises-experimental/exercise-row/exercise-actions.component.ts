import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, output, signal, viewChild, viewChildren } from '@angular/core';
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
 * A single exercise action. `quiz` group items are the quiz lifecycle buttons: they always stay visible and never
 * overflow into the ellipsis menu. `main` group items collapse into the ellipsis from the left when space is tight
 * (Delete is rightmost, so it overflows last).
 */
interface ActionItem {
    id: string;
    label: string;
    icon: IconProp;
    styleClass: string;
    group: 'quiz' | 'main';
    kind: 'link' | 'button' | 'delete' | 'batches';
    link?: (string | number)[];
    onClick?: () => void;
}

interface ActionLayout {
    visibleQuiz: ActionItem[];
    visibleMain: ActionItem[];
    overflowMain: ActionItem[];
}

// Approximate flex gap (gap-1 = 0.25rem) added per item when summing widths.
const GAP_PX = 4;

@Component({
    selector: 'jhi-exercise-actions',
    templateUrl: './exercise-actions.component.html',
    styleUrl: './exercise-actions.component.scss',
    imports: [RouterLink, NgTemplateOutlet, FaIconComponent, PopoverModule, DeleteButtonDirective, QuizExerciseLifecycleButtonsComponent],
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

    private readonly host = inject(ElementRef);
    private readonly destroyRef = inject(DestroyRef);
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
    private readonly measureItems = viewChildren<ElementRef<HTMLElement>>('measureItem');
    private readonly ellipsisMeasure = viewChild<ElementRef<HTMLElement>>('ellipsisMeasure');
    private readonly separatorMeasure = viewChild<ElementRef<HTMLElement>>('separatorMeasure');
    private readonly measureContainer = viewChild<ElementRef<HTMLElement>>('measureContainer');
    private readonly quizLifecycle = viewChild(QuizExerciseLifecycleButtonsComponent);

    private readonly containerWidth = signal(0);
    private readonly measuredWidths = signal<Map<string, number>>(new Map());
    private readonly ellipsisWidth = signal(0);
    private readonly separatorWidth = signal(0);

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
                label: 'Set Visible',
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
                label: 'Start',
                icon: faPlayCircle,
                styleClass: 'btn-success',
                group: 'quiz',
                kind: 'button',
                onClick: () => this.quizLifecycle()?.startQuiz(),
            });
        }
        // Batched: a single trigger that opens a popover listing each batch (id, password, status) and adds new ones.
        if (this.showBatchMenu()) {
            items.push({ id: 'batches', label: `Batches (${quiz.quizBatches?.length ?? 0})`, icon: faBoxesStacked, styleClass: 'btn-primary', group: 'quiz', kind: 'batches' });
        }
        // End: a running non-synchronized quiz that has not ended yet (synchronized quizzes end via their duration).
        if ((quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE) && quiz.quizMode !== QuizMode.SYNCHRONIZED && quiz.isAtLeastInstructor && !quiz.quizEnded) {
            items.push({ id: 'end', label: 'End', icon: faStopCircle, styleClass: 'btn-danger', group: 'quiz', kind: 'button', onClick: () => this.quizLifecycle()?.endQuiz() });
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
                label: 'Teams',
                icon: faUsers,
                styleClass: 'btn-primary',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, 'exercises', ex.id!, 'teams'],
            });
        }
        items.push({
            id: 'participations',
            label: 'Participations',
            icon: faListAlt,
            styleClass: 'btn-primary',
            group: 'main',
            kind: 'link',
            link: ['/course-management', cid, seg, ex.id!, 'participations'],
        });
        items.push({ id: 'scores', label: 'Scores', icon: faTable, styleClass: 'btn-info', group: 'main', kind: 'link', link: ['/course-management', cid, seg, ex.id!, 'scores'] });
        if (ex.type === ExerciseType.QUIZ) {
            const q = ex as QuizExercise;
            items.push({
                id: 'statistics',
                label: 'Statistics',
                icon: faChartBar,
                styleClass: 'btn-info',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'quiz-point-statistic'],
            });
            items.push({
                id: 'preview',
                label: 'Preview',
                icon: faEye,
                styleClass: 'btn-success',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'preview'],
            });
            items.push({
                id: 'solution',
                label: 'Solution',
                icon: faLightbulb,
                styleClass: 'btn-success',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, seg, ex.id!, 'solution'],
            });
            if (q.quizEnded) {
                items.push({
                    id: 're-evaluate',
                    label: 'Re-evaluate',
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
                label: 'Examples',
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
                label: 'Edit in Editor',
                icon: faPencilAlt,
                styleClass: 'btn-warning',
                group: 'main',
                kind: 'link',
                link: ['/course-management', cid, 'programming-exercises', ex.id!, 'code-editor', RepositoryType.TEMPLATE, -1],
            });
        }
        items.push({ id: 'edit', label: 'Edit', icon: faWrench, styleClass: 'btn-warning', group: 'main', kind: 'link', link: ['/course-management', cid, seg, ex.id!, 'edit'] });
        items.push({ id: 'delete', label: 'Delete', icon: faTrash, styleClass: 'btn-danger', group: 'main', kind: 'delete' });
        return items;
    });

    readonly allActions = computed<ActionItem[]>(() => [...this.quizActions(), ...this.mainActions()]);

    readonly deletionSummary = computed<Observable<EntitySummary>>(() => this.exerciseService.getDeletionSummary(this.exercise()));

    /** Placeholders for the delete confirmation question (`{{ courseType }}` / `{{ courseTitle }}`). */
    readonly deleteTranslateValues = computed<{ [key: string]: unknown }>(() => {
        const course = this.course();
        return {
            courseTitle: course?.title,
            courseType: this.translateService.instant(course?.testCourse ? 'artemisApp.exercise.delete.testCourse' : 'artemisApp.exercise.delete.realCourse'),
        };
    });

    private readonly layout = computed<ActionLayout>(() => {
        const quiz = this.quizActions();
        const main = this.mainActions();
        const widths = this.measuredWidths();
        const available = this.containerWidth();

        // Before measurement, show everything.
        if (available === 0 || widths.size === 0) {
            return { visibleQuiz: quiz, visibleMain: main, overflowMain: [] };
        }

        const widthOf = (item: ActionItem) => (widths.get(item.id) ?? 0) + GAP_PX;
        const sep = this.separatorWidth() + 2 * GAP_PX;
        const bothPresent = quiz.length > 0 && main.length > 0;

        const totalAll = quiz.reduce((sum, item) => sum + widthOf(item), 0) + main.reduce((sum, item) => sum + widthOf(item), 0) + (bothPresent ? sep : 0);
        if (totalAll <= available) {
            return { visibleQuiz: quiz, visibleMain: main, overflowMain: [] };
        }

        // Quiz lifecycle buttons always stay visible; only main actions collapse into the ellipsis. Reserve the quiz
        // group width up front and fill the remaining budget with main actions.
        const ellipsis = this.ellipsisWidth() + GAP_PX;
        const quizGroupWidth = quiz.reduce((sum, item) => sum + widthOf(item), 0) + (bothPresent ? sep : 0);
        let budget = available - ellipsis - quizGroupWidth;

        let keepFrom = main.length;
        for (let i = main.length - 1; i >= 0; i--) {
            if (budget >= widthOf(main[i])) {
                budget -= widthOf(main[i]);
                keepFrom = i;
            } else {
                break;
            }
        }

        return {
            visibleQuiz: quiz,
            visibleMain: main.slice(keepFrom),
            overflowMain: main.slice(0, keepFrom),
        };
    });

    readonly visibleQuiz = computed(() => this.layout().visibleQuiz);
    readonly visibleMain = computed(() => this.layout().visibleMain);
    readonly overflowMain = computed(() => this.layout().overflowMain);
    readonly hasOverflow = computed(() => this.overflowMain().length > 0);

    constructor() {
        afterNextRender(() => {
            const observer = new ResizeObserver(() => this.measure());
            observer.observe(this.host.nativeElement);
            const measureEl = this.measureContainer()?.nativeElement;
            if (measureEl) {
                observer.observe(measureEl);
            }
            this.destroyRef.onDestroy(() => observer.disconnect());
            this.measure();
        });
    }

    private measure(): void {
        const widths = new Map<string, number>();
        for (const ref of this.measureItems()) {
            const el = ref.nativeElement;
            const id = el.getAttribute('data-id');
            if (id) {
                widths.set(id, el.offsetWidth);
            }
        }
        this.measuredWidths.set(widths);
        this.ellipsisWidth.set(this.ellipsisMeasure()?.nativeElement.offsetWidth ?? 40);
        this.separatorWidth.set(this.separatorMeasure()?.nativeElement.offsetWidth ?? 1);
        this.containerWidth.set(this.host.nativeElement.clientWidth);
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
