import { Component, computed, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown, faCaretUp, faSort } from '@fortawesome/free-solid-svg-icons';
import { TableModule, TableRowReorderEvent } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { moveItemInArray } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DifficultyLevel, Exercise, ExerciseType, IncludedInOverallScore, getExerciseUrlSegment, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseActionsComponent } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-actions.component';

type SortColumn = 'title' | 'dueDate' | 'points' | 'difficulty';

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
        TranslateDirective,
        ArtemisDatePipe,
        ExerciseCategoriesComponent,
        ExerciseActionsComponent,
    ],
})
export class ExerciseTableComponent {
    readonly exercises = input.required<Exercise[]>();
    readonly group = input<CourseExerciseGroup | undefined>(undefined);
    readonly courseId = input.required<number>();
    readonly showDragHandle = input<boolean>(false);
    readonly showGroupSelector = input<boolean>(false);
    readonly showTypeIcon = input<boolean>(false);
    readonly showCheckbox = input<boolean>(false);
    readonly selectedIds = input<Set<number>>(new Set());
    readonly groups = input<CourseExerciseGroup[]>([]);
    /** Route segments inserted between courseId and urlSegment in the exercise title link. Used by versioned views to keep the mock interceptor active. */
    readonly overviewRouteMiddleSegments = input<string[]>([]);

    readonly groupChange = output<TableGroupChange>();
    readonly groupCreate = output<Exercise>();
    readonly rowsReordered = output<Exercise[]>();
    readonly exerciseUpdated = output<Exercise>();
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

    readonly sortColumn = signal<SortColumn>('title');
    readonly sortAsc = signal(true);

    readonly sortedExercises = computed(() => {
        const col = this.sortColumn();
        const asc = this.sortAsc();
        return [...this.exercises()].sort((a, b) => {
            let cmp = 0;
            switch (col) {
                case 'title':
                    cmp = (a.title ?? '').localeCompare(b.title ?? '');
                    break;
                case 'dueDate': {
                    const da = effectiveDate(a, this.group(), 'dueDate');
                    const db = effectiveDate(b, this.group(), 'dueDate');
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
        { label: 'No group', value: undefined as number | undefined },
        ...this.groups().map((g) => ({ label: g.title ?? `Group ${g.id}`, value: g.id as number | undefined })),
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

    onRowReorder(event: TableRowReorderEvent): void {
        if (event.dragIndex === undefined || event.dropIndex === undefined) return;
        const reordered = [...this.sortedExercises()];
        moveItemInArray(reordered, event.dragIndex, event.dropIndex);
        this.rowsReordered.emit(reordered);
    }

    urlSegment(exercise: Exercise): string {
        return getExerciseUrlSegment(exercise.type);
    }

    titleLink(exercise: Exercise): (string | number)[] {
        return ['/course-management', this.courseId(), ...this.overviewRouteMiddleSegments(), this.urlSegment(exercise), exercise.id!];
    }

    icon(exercise: Exercise) {
        return getIcon(exercise.type);
    }

    effectiveReleaseDate(exercise: Exercise) {
        return effectiveDate(exercise, this.group(), 'releaseDate');
    }

    effectiveDueDate(exercise: Exercise) {
        return effectiveDate(exercise, this.group(), 'dueDate');
    }

    effectiveAssessmentDueDate(exercise: Exercise) {
        return effectiveDate(exercise, this.group(), 'assessmentDueDate');
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

    onGroupCreate(exercise: Exercise): void {
        this.groupCreate.emit(exercise);
    }

    asQuiz(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }

    quizStatusLabel(exercise: QuizExercise): string {
        switch (exercise.status) {
            case QuizStatus.INVISIBLE:
                return 'Invisible';
            case QuizStatus.VISIBLE:
                return 'Visible';
            case QuizStatus.ACTIVE:
                return 'Active';
            case QuizStatus.OPEN_FOR_PRACTICE:
                return 'Practice';
            default:
                return '—';
        }
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
