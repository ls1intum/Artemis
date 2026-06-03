import { Component, computed, inject, input, output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CdkDragHandle } from '@angular/cdk/drag-drop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    faChartBar,
    faClipboardList,
    faEye,
    faGripVertical,
    faLayerGroup,
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
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { PopoverModule } from 'primeng/popover';
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
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ExerciseManagementDevSettingsService } from 'app/core/course/manage/exercises-experimental/dev-settings/exercise-management-dev-settings.service';

@Component({
    selector: 'jhi-exercise-row-compact',
    templateUrl: './exercise-row-compact.component.html',
    styleUrl: './exercise-row-compact.component.scss',
    imports: [
        RouterLink,
        FormsModule,
        CdkDragHandle,
        FaIconComponent,
        CheckboxModule,
        SelectModule,
        PopoverModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        DeleteButtonDirective,
        ExerciseCategoriesComponent,
    ],
})
export class ExerciseRowCompactComponent {
    readonly exercise = input.required<Exercise>();
    /** The group the exercise belongs to, if any — its dates override the exercise's own dates. */
    readonly group = input<CourseExerciseGroup | undefined>(undefined);
    readonly courseId = input.required<number>();
    readonly showTypeIcon = input<boolean>(true);
    readonly showDragHandle = input<boolean>(false);
    readonly showCheckbox = input<boolean>(false);
    readonly selected = input<boolean>(false);
    readonly owningGroup = input<CourseExerciseGroup | undefined>(undefined);
    readonly groups = input<CourseExerciseGroup[]>([]);

    readonly selectedChange = output<void>();
    readonly groupChange = output<CourseExerciseGroup | undefined>();
    readonly groupCreate = output<void>();
    readonly exerciseUpdated = output<Exercise>();

    readonly owningGroupName = computed(() => this.owningGroup()?.title);
    readonly groupOptions = computed(() => [{ label: 'No group', value: undefined }, ...this.groups().map((g) => ({ label: g.title ?? `Group ${g.id}`, value: g.id }))]);

    readonly deleted = output<void>();

    protected readonly ExerciseType = ExerciseType;
    protected readonly ExerciseMode = ExerciseMode;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly DifficultyLevel = DifficultyLevel;
    protected readonly RepositoryType = RepositoryType;
    protected readonly QuizStatus = QuizStatus;
    protected readonly QuizMode = QuizMode;

    protected readonly faGripVertical = faGripVertical;
    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faTable = faTable;
    protected readonly faListAlt = faListAlt;
    protected readonly faWrench = faWrench;
    protected readonly faTrash = faTrash;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faEye = faEye;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly faStopCircle = faStopCircle;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboardList = faClipboardList;
    protected readonly faLightbulb = faLightbulb;
    protected readonly faRedo = faRedo;
    protected readonly faUsers = faUsers;
    protected readonly faPlus = faPlus;

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    private readonly textExerciseService = inject(TextExerciseService);
    private readonly fileUploadExerciseService = inject(FileUploadExerciseService);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly eventManager = inject(EventManager);
    private readonly profileService = inject(ProfileService);
    readonly devSettings = inject(ExerciseManagementDevSettingsService);

    readonly localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);

    readonly icon = computed(() => getIcon(this.exercise().type));
    readonly urlSegment = computed(() => getExerciseUrlSegment(this.exercise().type));
    readonly effectiveReleaseDate = computed(() => effectiveDate(this.exercise(), this.group(), 'releaseDate'));
    readonly effectiveDueDate = computed(() => effectiveDate(this.exercise(), this.group(), 'dueDate'));
    readonly dueDateOverridden = computed(() => !!this.group()?.dueDate);
    readonly releaseDateOverridden = computed(() => !!this.group()?.releaseDate);

    readonly difficultyBadgeClass = computed(() => {
        switch (this.exercise().difficulty) {
            case DifficultyLevel.EASY:
                return 'bg-success';
            case DifficultyLevel.MEDIUM:
                return 'bg-warning';
            case DifficultyLevel.HARD:
                return 'bg-danger';
            default:
                return 'bg-secondary';
        }
    });

    onGroupSelect(groupId: number | undefined): void {
        this.groupChange.emit(this.groups().find((g) => g.id === groupId));
    }

    asQuiz(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }

    setQuizVisible(quiz: QuizExercise): void {
        this.exerciseUpdated.emit({ ...quiz, status: QuizStatus.VISIBLE } as QuizExercise);
    }

    startQuiz(quiz: QuizExercise): void {
        this.exerciseUpdated.emit({ ...quiz, status: QuizStatus.ACTIVE, quizStarted: true } as QuizExercise);
    }

    endQuiz(quiz: QuizExercise): void {
        this.exerciseUpdated.emit({ ...quiz, status: QuizStatus.INVISIBLE, quizEnded: true, quizStarted: false } as QuizExercise);
    }

    quizStatusLabel(quiz: QuizExercise): string {
        switch (quiz.status) {
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

    addBatch(_quiz: QuizExercise): void {}

    quizStatusClass(quiz: QuizExercise): string {
        switch (quiz.status) {
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

    fetchExerciseDeletionSummary(): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(this.exercise());
    }

    /** Deletes the exercise using the service that matches its type. */
    deleteExercise(event: { [key: string]: boolean }): void {
        const exercise = this.exercise();
        switch (exercise.type) {
            case ExerciseType.TEXT:
                this.runDelete(this.textExerciseService.delete(exercise.id!), 'textExerciseListModification');
                break;
            case ExerciseType.FILE_UPLOAD:
                this.runDelete(this.fileUploadExerciseService.delete(exercise.id!), 'fileUploadExerciseListModification');
                break;
            case ExerciseType.QUIZ:
                this.runDelete(this.quizExerciseService.delete(exercise.id!), 'quizExerciseListModification');
                break;
            case ExerciseType.MODELING:
                this.runDelete(this.modelingExerciseService.delete(exercise.id!), 'modelingExerciseListModification');
                break;
            case ExerciseType.PROGRAMMING:
                this.runDelete(
                    this.programmingExerciseService.delete(exercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans),
                    'programmingExerciseListModification',
                );
                break;
        }
    }

    private runDelete(deletion$: Observable<unknown>, eventName: string): void {
        deletion$.subscribe({
            next: () => {
                this.eventManager.broadcast({ name: eventName, content: 'Deleted an exercise' });
                this.dialogErrorSource.next('');
                this.deleted.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
