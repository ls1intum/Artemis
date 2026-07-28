import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBars } from '@fortawesome/free-solid-svg-icons';
import { TumUiSelectComponent } from 'app/shared-ui/tum-ui/select/tum-ui-select.component';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDragPreview, CdkDropList } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Exercise, ExerciseType, IncludedInOverallScore, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import dayjs from 'dayjs/esm';

export interface ExamTableGroupChange {
    exercise: Exercise;
    group: ExerciseGroup;
}

@Component({
    selector: 'jhi-exam-exercise-table',
    templateUrl: './exam-exercise-table.component.html',
    styleUrl: './exam-exercise-table.component.scss',
    // Floor the actions column at `--actions-min-width` — the widest always-visible test-run-warning + ellipsis width
    // the rows actually report — so it collapses every main button into the ellipsis before the table scrolls, yet
    // never clips the (non-collapsing) warning. Stays unset when no row reports reserved content, so the column
    // collapses fully (SCSS default). Mirrors the course exercise-table's `--actions-min-width` binding.
    host: { '[style.--actions-min-width]': 'actionsMinWidthVar()' },
    imports: [
        RouterLink,
        FormsModule,
        FaIconComponent,
        TumUiTableDirective,
        TumUiSelectComponent,
        CdkDropList,
        CdkDrag,
        CdkDragHandle,
        CdkDragPreview,
        TranslateDirective,
        ArtemisTranslatePipe,
        ExamExerciseRowButtonsComponent,
        ProgrammingExerciseGroupCellComponent,
        QuizExerciseGroupCellComponent,
        ModelingExerciseGroupCellComponent,
        FileUploadExerciseGroupCellComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExamExerciseTableComponent {
    private readonly translateService = inject(TranslateService);

    readonly exercises = input.required<Exercise[]>();
    readonly group = input.required<ExerciseGroup>();
    readonly groups = input<ExerciseGroup[]>([]);
    readonly course = input.required<Course>();
    readonly exam = input.required<Exam>();
    readonly courseId = input.required<number>();
    readonly examId = input.required<number>();
    readonly latestIndividualEndDate = input<dayjs.Dayjs>();
    readonly localCIEnabled = input<boolean>(true);
    readonly disabledExerciseTypes = input<ExerciseType[]>([]);
    /** CDK drop-list id for this table's exercises (the owning group's id). */
    readonly dropListId = input<string>('');
    /** Ids of the sibling exam exercise tables this one can exchange exercises with (enables cross-group drag-and-drop). */
    readonly connectedDropLists = input<string[]>([]);

    readonly groupChange = output<ExamTableGroupChange>();
    readonly exerciseDeleted = output<Exercise>();

    protected readonly exerciseType = ExerciseType;
    protected readonly faBars = faBars;

    /** The exercise types present in this group's exercises — drives which type-specific columns are shown. */
    private readonly presentTypes = computed<Set<ExerciseType>>(() => new Set(this.exercises().map((exercise) => exercise.type!)));

    protected readonly hasProgramming = computed(() => this.presentTypes().has(ExerciseType.PROGRAMMING));
    protected readonly hasQuiz = computed(() => this.presentTypes().has(ExerciseType.QUIZ));
    protected readonly hasModeling = computed(() => this.presentTypes().has(ExerciseType.MODELING));
    protected readonly hasFileUpload = computed(() => this.presentTypes().has(ExerciseType.FILE_UPLOAD));
    protected readonly hasAssessmentModeColumn = computed(() => this.hasProgramming() || this.presentTypes().has(ExerciseType.TEXT) || this.hasModeling());

    /** Cross-group drag is only meaningful with at least one other group to drop into. */
    protected readonly showDragHandle = computed(() => this.groups().length > 1);

    protected readonly groupOptions = computed(() => this.groups().map((g) => ({ label: g.title ?? `#${g.id}`, value: g.id })));

    /**
     * Largest actions-column width (px) any row has reported for its always-visible reserved content (the test-run
     * warning) + ellipsis. Kept as a running max so the shared column fits the widest row. Only grows: if the widest
     * row later disappears the floor may stay marginally larger than needed, at worst scrolling a touch sooner —
     * never clipping the warning. Stays 0 when no row reports reserved content.
     */
    private readonly maxActionsMinWidth = signal(0);
    /** CSS value for the actions-column floor, or undefined when no row needs it (so the SCSS default applies). */
    protected readonly actionsMinWidthVar = computed(() => {
        const width = this.maxActionsMinWidth();
        return width > 0 ? `${width}px` : undefined;
    });

    onActionsMinWidth(width: number): void {
        if (width > this.maxActionsMinWidth()) {
            this.maxActionsMinWidth.set(width);
        }
    }

    icon(exercise: Exercise) {
        return getIcon(exercise.type);
    }

    isIncludedInScore(exercise: Exercise): string {
        switch (exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.translateService.instant('artemisApp.exercise.bonus');
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.translateService.instant('artemisApp.exercise.yes');
            case IncludedInOverallScore.NOT_INCLUDED:
                return this.translateService.instant('artemisApp.exercise.no');
            default:
                return '';
        }
    }

    isExerciseTypeDisabled(exercise: Exercise): boolean {
        return this.disabledExerciseTypes().includes(exercise.type!);
    }

    titleLink(exercise: Exercise): (string | number)[] {
        return ['/course-management', this.courseId(), 'exams', this.examId(), 'exercise-groups', this.group().id!, exercise.type + '-exercises', exercise.id!];
    }

    onGroupSelect(exercise: Exercise, groupId: number | undefined): void {
        const targetGroup = this.groups().find((g) => g.id === groupId);
        if (targetGroup && targetGroup.id !== this.group().id) {
            this.groupChange.emit({ exercise, group: targetGroup });
        }
    }

    onDrop(event: CdkDragDrop<Exercise[]>): void {
        // Same-container drops are ignored: there is no manual within-group order to preserve.
        if (event.previousContainer !== event.container) {
            this.groupChange.emit({ exercise: event.item.data, group: this.group() });
        }
    }
}
