import { Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faCaretDown,
    faCaretUp,
    faChartBar,
    faClipboardList,
    faEye,
    faLayerGroup,
    faLightbulb,
    faListAlt,
    faPencilAlt,
    faPlayCircle,
    faPlus,
    faRedo,
    faSort,
    faStopCircle,
    faTable,
    faTrash,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { TableModule, TableRowReorderEvent } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { moveItemInArray } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import {
    DifficultyLevel,
    Exercise,
    ExerciseMode,
    ExerciseType,
    IncludedInOverallScore,
    getExerciseUrlSegment,
    getIcon,
} from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ExerciseManagementDevSettingsService } from 'app/core/course/manage/exercises-experimental/dev-settings/exercise-management-dev-settings.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';

type SortColumn = 'title' | 'releaseDate' | 'dueDate' | 'assessmentDueDate' | 'points' | 'difficulty';

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
        PopoverModule,
        ButtonModule,
        CheckboxModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        DeleteButtonDirective,
        ExerciseCategoriesComponent,
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

    readonly groupChange = output<TableGroupChange>();
    readonly groupCreate = output<Exercise>();
    readonly rowsReordered = output<Exercise[]>();
    readonly exerciseUpdated = output<Exercise>();
    readonly selectionToggle = output<number>();
    readonly selectionAllChange = output<boolean>();

    protected readonly ExerciseType = ExerciseType;
    protected readonly ExerciseMode = ExerciseMode;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly RepositoryType = RepositoryType;
    protected readonly AssessmentType = AssessmentType;
    protected readonly QuizStatus = QuizStatus;
    protected readonly QuizMode = QuizMode;

    protected readonly faSort = faSort;
    protected readonly faCaretUp = faCaretUp;
    protected readonly faCaretDown = faCaretDown;
    protected readonly faTable = faTable;
    protected readonly faListAlt = faListAlt;
    protected readonly faWrench = faWrench;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faEye = faEye;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faStopCircle = faStopCircle;
    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboardList = faClipboardList;
    protected readonly faLightbulb = faLightbulb;
    protected readonly faRedo = faRedo;
    protected readonly faUsers = faUsers;
    protected readonly faPlus = faPlus;

    readonly sortColumn = signal<SortColumn>('title');
    readonly sortAsc = signal(true);

    protected readonly devSettings = inject(ExerciseManagementDevSettingsService);

    private readonly dialogErrorSources = new Map<number, Subject<string>>();

    private readonly textExerciseService = inject(TextExerciseService);
    private readonly fileUploadExerciseService = inject(FileUploadExerciseService);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly eventManager = inject(EventManager);

    readonly sortedExercises = computed(() => {
        const col = this.sortColumn();
        const asc = this.sortAsc();
        return [...this.exercises()].sort((a, b) => {
            let cmp = 0;
            switch (col) {
                case 'title':
                    cmp = (a.title ?? '').localeCompare(b.title ?? '');
                    break;
                case 'releaseDate': {
                    const da = effectiveDate(a, this.group(), 'releaseDate');
                    const db = effectiveDate(b, this.group(), 'releaseDate');
                    cmp = (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
                    break;
                }
                case 'dueDate': {
                    const da = effectiveDate(a, this.group(), 'dueDate');
                    const db = effectiveDate(b, this.group(), 'dueDate');
                    cmp = (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
                    break;
                }
                case 'assessmentDueDate': {
                    const da = effectiveDate(a, this.group(), 'assessmentDueDate');
                    const db = effectiveDate(b, this.group(), 'assessmentDueDate');
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

    isAutomatic(exercise: Exercise): boolean {
        return (exercise as any).assessmentType === AssessmentType.AUTOMATIC;
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

    addBatch(_quiz: QuizExercise): void {}

    setQuizVisible(exercise: QuizExercise): void {
        this.exerciseUpdated.emit({ ...exercise, status: QuizStatus.VISIBLE } as QuizExercise);
    }

    startQuiz(exercise: QuizExercise): void {
        this.exerciseUpdated.emit({ ...exercise, status: QuizStatus.ACTIVE, quizStarted: true } as QuizExercise);
    }

    endQuiz(exercise: QuizExercise): void {
        this.exerciseUpdated.emit({ ...exercise, status: QuizStatus.INVISIBLE, quizEnded: true, quizStarted: false } as QuizExercise);
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

    dialogError$(exercise: Exercise): Observable<string> {
        const id = exercise.id ?? -1;
        if (!this.dialogErrorSources.has(id)) {
            this.dialogErrorSources.set(id, new Subject<string>());
        }
        return this.dialogErrorSources.get(id)!.asObservable();
    }

    fetchExerciseDeletionSummary(exercise: Exercise): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(exercise);
    }

    deleteExercise(exercise: Exercise, event: { [key: string]: boolean }): void {
        const src = this.dialogErrorSources.get(exercise.id ?? -1) ?? new Subject<string>();
        const finish = (obs: Observable<unknown>, evtName: string) =>
            obs.subscribe({
                next: () => {
                    this.eventManager.broadcast({ name: evtName, content: 'Deleted an exercise' });
                    src.next('');
                },
                error: (e: HttpErrorResponse) => src.next(e.message),
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
