import { Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown, faCaretUp, faSort } from '@fortawesome/free-solid-svg-icons';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TooltipModule } from 'primeng/tooltip';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDragPreview, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DifficultyLevel, Exercise, ExerciseType, IncludedInOverallScore, getExerciseUrlSegment, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/course-exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseActionsComponent } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-actions.component';

type SortColumn = 'title' | 'dueDate' | 'points' | 'difficulty';
/** Sentinel sort state set after a manual drag-and-drop reorder: no column is sorted, rows keep their dragged order. */
type SortState = SortColumn | 'manual';

const DIFFICULTY_ORDER: Record<string, number> = {
    [DifficultyLevel.EASY]: 0,
    [DifficultyLevel.MEDIUM]: 1,
    [DifficultyLevel.HARD]: 2,
};

export interface TableGroupChange {
    exercise: Exercise;
    group: CourseExerciseGroup | undefined;
}

@Component({
    selector: 'jhi-exercise-table',
    templateUrl: './exercise-table.component.html',
    styleUrl: './exercise-table.component.scss',
    imports: [
        RouterLink,
        FormsModule,
        FaIconComponent,
        TableModule,
        SelectModule,
        CheckboxModule,
        TooltipModule,
        CdkDropList,
        CdkDrag,
        CdkDragHandle,
        CdkDragPreview,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ExerciseCategoriesComponent,
        ExerciseActionsComponent,
    ],
})
export class ExerciseTableComponent {
    private readonly translateService = inject(TranslateService);

    readonly exercises = input.required<Exercise[]>();
    readonly group = input<CourseExerciseGroup | undefined>(undefined);
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);
    readonly showDragHandle = input<boolean>(false);
    readonly showGroupSelector = input<boolean>(false);
    readonly showTypeIcon = input<boolean>(false);
    readonly showCheckbox = input<boolean>(false);
    readonly selectedIds = input<Set<number>>(new Set());
    readonly groups = input<CourseExerciseGroup[]>([]);
    /** CDK drop-list id for this table's exercises (the owning bucket's id). */
    readonly dropListId = input<string>('');
    /** Ids of the sibling exercise tables this one can exchange exercises with (enables cross-group drag-and-drop). */
    readonly connectedDropLists = input<string[]>([]);

    readonly groupChange = output<TableGroupChange>();
    readonly rowsReordered = output<Exercise[]>();
    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();
    readonly selectionToggle = output<number>();
    readonly selectionAllChange = output<boolean>();

    protected readonly ExerciseType = ExerciseType;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly QuizStatus = QuizStatus;
    protected readonly QuizMode = QuizMode;

    protected readonly faSort = faSort;
    protected readonly faCaretUp = faCaretUp;
    protected readonly faCaretDown = faCaretDown;

    readonly sortColumn = signal<SortState>('title');
    readonly sortAsc = signal(true);

    readonly sortedExercises = computed(() => {
        const col = this.sortColumn();
        const asc = this.sortAsc();
        // Manual order: a drag-and-drop reorder takes precedence over column sorting until a header is clicked again.
        if (col === 'manual') {
            return [...this.exercises()];
        }
        return [...this.exercises()].sort((a, b) => {
            let cmp = 0;
            switch (col) {
                case 'title':
                    cmp = (a.title ?? '').localeCompare(b.title ?? '');
                    break;
                case 'dueDate': {
                    const da = effectiveDate(a, this.effectiveGroupFor(a), 'dueDate');
                    const db = effectiveDate(b, this.effectiveGroupFor(b), 'dueDate');
                    cmp = (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
                    break;
                }
                case 'points':
                    cmp = (a.maxPoints ?? 0) - (b.maxPoints ?? 0);
                    break;
                case 'difficulty':
                    cmp = (DIFFICULTY_ORDER[a.difficulty ?? ''] ?? -1) - (DIFFICULTY_ORDER[b.difficulty ?? ''] ?? -1);
                    break;
            }
            return asc ? cmp : -cmp;
        });
    });

    readonly allSelected = computed(() => {
        const ids = this.selectedIds();
        const exercises = this.sortedExercises();
        return exercises.length > 0 && exercises.every((e) => e.id === undefined || ids.has(e.id));
    });

    readonly someSelected = computed(() => {
        const ids = this.selectedIds();
        return this.sortedExercises().some((e) => e.id !== undefined && ids.has(e.id)) && !this.allSelected();
    });

    readonly groupOptions = computed(() => [
        { label: this.translateService.instant('artemisApp.exerciseManagement.table.noGroup'), value: undefined as number | undefined },
        ...this.groups().map((g) => ({
            label: g.title ?? this.translateService.instant('artemisApp.exerciseManagement.bucket.group', { id: g.id }),
            value: g.id as number | undefined,
        })),
    ]);

    sortBy(col: SortColumn): void {
        if (this.sortColumn() === col) {
            this.sortAsc.set(!this.sortAsc());
        } else {
            this.sortColumn.set(col);
            this.sortAsc.set(true);
        }
    }

    sortIcon(col: SortColumn) {
        if (this.sortColumn() !== col) return this.faSort;
        return this.sortAsc() ? this.faCaretUp : this.faCaretDown;
    }

    onDrop(event: CdkDragDrop<Exercise[]>): void {
        if (event.previousContainer === event.container) {
            // Reorder within this group: keep the dragged order and stop applying column sorting.
            const reordered = [...this.sortedExercises()];
            moveItemInArray(reordered, event.previousIndex, event.currentIndex);
            this.sortColumn.set('manual');
            this.rowsReordered.emit(reordered);
        } else {
            // Dropped from another group's table: move the exercise into this table's group.
            this.groupChange.emit({ exercise: event.item.data, group: this.group() });
        }
    }

    urlSegment(exercise: Exercise): string {
        return getExerciseUrlSegment(exercise.type);
    }

    titleLink(exercise: Exercise): (string | number)[] {
        return ['/course-management', this.courseId(), this.urlSegment(exercise), exercise.id!];
    }

    icon(exercise: Exercise) {
        return getIcon(exercise.type);
    }

    /**
     * The group whose timeline governs this exercise. In the group view the bucket's group is passed in
     * directly; in the type/week/list views the bucket has no single group, so we resolve the exercise's
     * owning group from the full groups list. This keeps the displayed dates consistent across all views.
     */
    private effectiveGroupFor(exercise: Exercise): CourseExerciseGroup | undefined {
        return this.group() ?? this.owningGroupForExercise(exercise);
    }

    effectiveReleaseDate(exercise: Exercise) {
        return effectiveDate(exercise, this.effectiveGroupFor(exercise), 'releaseDate');
    }

    effectiveDueDate(exercise: Exercise) {
        return effectiveDate(exercise, this.effectiveGroupFor(exercise), 'dueDate');
    }

    effectiveAssessmentDueDate(exercise: Exercise) {
        return effectiveDate(exercise, this.effectiveGroupFor(exercise), 'assessmentDueDate');
    }

    difficultyBadgeClass(exercise: Exercise): string {
        switch (exercise.difficulty) {
            case DifficultyLevel.EASY:
                return 'bg-success';
            case DifficultyLevel.MEDIUM:
                return 'bg-warning';
            case DifficultyLevel.HARD:
                return 'bg-danger';
            default:
                return 'bg-secondary';
        }
    }

    owningGroupForExercise(exercise: Exercise): CourseExerciseGroup | undefined {
        return this.groups().find((g) => g.exercises?.some((e) => e.id === exercise.id));
    }

    owningGroupId(exercise: Exercise): number | undefined {
        return this.owningGroupForExercise(exercise)?.id;
    }

    onGroupSelect(exercise: Exercise, groupId: number | undefined): void {
        const group = this.groups().find((g) => g.id === groupId);
        this.groupChange.emit({ exercise, group });
    }

    asQuiz(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }

    protected readonly rowTrackBy = (_index: number, exercise: Exercise): unknown => {
        if (exercise.type !== ExerciseType.QUIZ) return exercise.id ?? exercise;
        const q = exercise as QuizExercise;
        // In zoneless Angular, ngFor embedded views may not re-evaluate when let-exercise gets a new
        // object reference with the same id. Including the properties that drive lifecycle-button
        // rendering forces the row to be destroyed/recreated when they change, so the fresh
        // exercise-actions instance always sees the up-to-date exercise.
        return `${exercise.id}|${q.exerciseVariantGroup?.id ?? ''}|${q.status ?? ''}|${q.visibleToStudents ?? ''}`;
    };

    /**
     * Only individual-mode quizzes support per-student dates, so only they can reasonably share a group's timeline.
     * Synchronized/batched quizzes must stay out of groups; the server enforces this independently.
     */
    isQuizNonIndividual(exercise: Exercise): boolean {
        return exercise.type === ExerciseType.QUIZ && this.asQuiz(exercise).quizMode !== undefined && this.asQuiz(exercise).quizMode !== QuizMode.INDIVIDUAL;
    }

    nonIndividualQuizTooltip(exercise: Exercise): string | undefined {
        return this.isQuizNonIndividual(exercise) ? this.translateService.instant('artemisApp.exerciseManagement.table.nonIndividualQuizTooltip') : undefined;
    }

    /** Translation key for the quiz status badge, or `undefined` when no badge should be shown. */
    quizStatusLabel(exercise: QuizExercise): string | undefined {
        switch (exercise.status) {
            case QuizStatus.INVISIBLE:
                return 'artemisApp.quizExercise.quizStatus.invisible';
            case QuizStatus.VISIBLE:
                return 'artemisApp.quizExercise.quizStatus.visible';
            case QuizStatus.ACTIVE:
                return 'artemisApp.quizExercise.quizStatus.active';
            case QuizStatus.OPEN_FOR_PRACTICE:
                return 'artemisApp.quizExercise.practiceMode';
            default:
                return undefined;
        }
    }

    /** Translation key for the quiz mode badge (e.g. `artemisApp.quizExercise.quizMode.synchronized`). */
    quizModeKey(exercise: QuizExercise): string {
        return `artemisApp.quizExercise.quizMode.${(exercise.quizMode ?? '').toLowerCase()}`;
    }

    quizStatusClass(exercise: QuizExercise): string {
        switch (exercise.status) {
            case QuizStatus.INVISIBLE:
                return 'bg-secondary';
            case QuizStatus.VISIBLE:
                return 'bg-info';
            case QuizStatus.ACTIVE:
                return 'bg-success';
            case QuizStatus.OPEN_FOR_PRACTICE:
                return 'bg-primary';
            default:
                return 'bg-light text-dark';
        }
    }
}
