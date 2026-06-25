import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, output, signal, viewChild, viewChildren } from '@angular/core';
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
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
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
 * A single exercise action. `quiz` group items are the quiz lifecycle buttons (edge case): they stay
 * visible longest and, when they do overflow, render at the top of the ellipsis menu with a separator.
 * `main` group items overflow left-to-right first (Delete is rightmost, so it overflows last).
 */
interface ActionItem {
    id: string;
    label: string;
    icon: IconProp;
    styleClass: string;
    group: 'quiz' | 'main';
    kind: 'link' | 'button' | 'delete';
    link?: (string | number)[];
    onClick?: () => void;
}

interface ActionLayout {
    visibleQuiz: ActionItem[];
    visibleMain: ActionItem[];
    overflowQuiz: ActionItem[];
    overflowMain: ActionItem[];
}

// Approximate flex gap (gap-1 = 0.25rem) added per item when summing widths.
const GAP_PX = 4;

@Component({
    selector: 'jhi-exercise-actions',
    templateUrl: './exercise-actions.component.html',
    styleUrl: './exercise-actions.component.scss',
    imports: [RouterLink, NgTemplateOutlet, FaIconComponent, PopoverModule, DeleteButtonDirective],
})
export class ExerciseActionsComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);

    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();

    protected readonly ExerciseType = ExerciseType;
    protected readonly faEllipsis = faEllipsis;

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
    private readonly measureItems = viewChildren<ElementRef<HTMLElement>>('measureItem');
    private readonly ellipsisMeasure = viewChild<ElementRef<HTMLElement>>('ellipsisMeasure');
    private readonly separatorMeasure = viewChild<ElementRef<HTMLElement>>('separatorMeasure');
    private readonly measureContainer = viewChild<ElementRef<HTMLElement>>('measureContainer');

    private readonly containerWidth = signal(0);
    private readonly measuredWidths = signal<Map<string, number>>(new Map());
    private readonly ellipsisWidth = signal(0);
    private readonly separatorWidth = signal(0);

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    /** Quiz lifecycle buttons (edge case): highest priority to stay visible. */
    readonly quizActions = computed<ActionItem[]>(() => {
        const ex = this.exercise();
        if (ex.type !== ExerciseType.QUIZ) return [];
        const q = ex as QuizExercise;
        const items: ActionItem[] = [];
        if (q.status === QuizStatus.INVISIBLE) {
            items.push({ id: 'set-visible', label: 'Set Visible', icon: faEye, styleClass: 'btn-warning', group: 'quiz', kind: 'button', onClick: () => this.setQuizVisible(q) });
        }
        if (q.status === QuizStatus.VISIBLE && q.quizMode === QuizMode.SYNCHRONIZED && !q.quizStarted) {
            items.push({ id: 'start', label: 'Start', icon: faPlayCircle, styleClass: 'btn-success', group: 'quiz', kind: 'button', onClick: () => this.startQuiz(q) });
        }
        if ((q.status === QuizStatus.VISIBLE || q.status === QuizStatus.ACTIVE) && q.quizMode === QuizMode.BATCHED) {
            items.push({ id: 'add-batch', label: 'Add Batch', icon: faPlus, styleClass: 'btn-primary', group: 'quiz', kind: 'button', onClick: () => this.addBatch(q) });
        }
        if ((q.status === QuizStatus.VISIBLE || q.status === QuizStatus.ACTIVE) && !q.quizEnded) {
            items.push({ id: 'end', label: 'End', icon: faStopCircle, styleClass: 'btn-danger', group: 'quiz', kind: 'button', onClick: () => this.endQuiz(q) });
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
            return { visibleQuiz: quiz, visibleMain: main, overflowQuiz: [], overflowMain: [] };
        }

        const widthOf = (item: ActionItem) => (widths.get(item.id) ?? 0) + GAP_PX;
        const sep = this.separatorWidth() + 2 * GAP_PX;
        const bothPresent = quiz.length > 0 && main.length > 0;

        const totalAll = quiz.reduce((sum, item) => sum + widthOf(item), 0) + main.reduce((sum, item) => sum + widthOf(item), 0) + (bothPresent ? sep : 0);
        if (totalAll <= available) {
            return { visibleQuiz: quiz, visibleMain: main, overflowQuiz: [], overflowMain: [] };
        }

        // Collapse left-to-right: quiz group first (as a unit), then main actions one by one.
        const ellipsis = this.ellipsisWidth() + GAP_PX;
        const quizGroupWidth = quiz.reduce((sum, item) => sum + widthOf(item), 0) + (quiz.length > 0 && main.length > 0 ? sep : 0);
        const mainTotalWidth = main.reduce((sum, item) => sum + widthOf(item), 0);

        const quizVisible = ellipsis + quizGroupWidth + mainTotalWidth <= available;
        let budget = available - ellipsis - (quizVisible ? quizGroupWidth : 0);

        // Fill from the right so Delete collapses last.
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
            visibleQuiz: quizVisible ? quiz : [],
            visibleMain: main.slice(keepFrom),
            overflowQuiz: quizVisible ? [] : quiz,
            overflowMain: main.slice(0, keepFrom),
        };
    });

    readonly visibleQuiz = computed(() => this.layout().visibleQuiz);
    readonly visibleMain = computed(() => this.layout().visibleMain);
    readonly overflowQuiz = computed(() => this.layout().overflowQuiz);
    readonly overflowMain = computed(() => this.layout().overflowMain);
    readonly hasOverflow = computed(() => this.overflowQuiz().length > 0 || this.overflowMain().length > 0);

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

    private setQuizVisible(exercise: QuizExercise): void {
        this.exerciseUpdated.emit({ ...exercise, status: QuizStatus.VISIBLE } as QuizExercise);
    }

    private startQuiz(exercise: QuizExercise): void {
        this.exerciseUpdated.emit({ ...exercise, status: QuizStatus.ACTIVE, quizStarted: true } as QuizExercise);
    }

    private endQuiz(exercise: QuizExercise): void {
        this.exerciseUpdated.emit({ ...exercise, status: QuizStatus.INVISIBLE, quizEnded: true, quizStarted: false } as QuizExercise);
    }

    private addBatch(_exercise: QuizExercise): void {}

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
