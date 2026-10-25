import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, Injector, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { FileUploadExercisePagingService } from 'app/fileupload/manage/services/file-upload-exercise-paging.service';
import { ModelingExercisePagingService } from 'app/modeling/manage/services/modeling-exercise-paging.service';
import { CodeAnalysisPagingService } from 'app/programming/manage/services/code-analysis-paging.service';
import { ProgrammingExercisePagingService } from 'app/programming/manage/services/programming-exercise-paging.service';
import { QuizExercisePagingService } from 'app/quiz/manage/service/quiz-exercise-paging.service';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';
import { TextExercisePagingService } from 'app/text/manage/text-exercise/service/text-exercise-paging.service';
import { ImportComponent } from 'app/shared-ui/import/import.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbHighlight } from '@ng-bootstrap/ng-bootstrap';
import { PaginatorModule } from 'primeng/paginator';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ExerciseCourseTitlePipe } from 'app/foundation/pipes/exercise-course-title.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { TumAetUiButtonComponent, TumAetUiDialogComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { faEye } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizQuestionPreviewComponent } from 'app/quiz/manage/quiz-question-preview/quiz-question-preview.component';
import { AlertService } from 'app/foundation/service/alert.service';

export interface ExerciseImportDialogData {
    exerciseType: ExerciseType;
    programmingLanguage?: ProgrammingLanguage;
}

/** The row the user asked a preview for, together with the complete quiz loaded for it. */
interface ExerciseImportPreview {
    exercise: Exercise;
    quiz: QuizExercise;
}

const DEFAULT_SORT_COLUMN = 'ID';

@Component({
    selector: 'jhi-exercise-import',
    templateUrl: './exercise-import.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        SortDirective,
        SortByDirective,
        FaIconComponent,
        NgbHighlight,
        ButtonComponent,
        PaginatorModule,
        ExerciseCourseTitlePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
        TumAetUiButtonComponent,
        TumAetUiDialogComponent,
        TumAetUiTooltipDirective,
        QuizQuestionPreviewComponent,
    ],
})
export class ExerciseImportComponent extends ImportComponent<Exercise> implements OnInit {
    private injector = inject(Injector);
    private quizExerciseService = inject(QuizExerciseService);
    private alertService = inject(AlertService);
    private document = inject(DOCUMENT);
    private previewDestroyRef = inject(DestroyRef);
    readonly ExerciseType = ExerciseType;

    exerciseType = input<ExerciseType | undefined>();

    /**
     * The programming language is only set when filtering for exercises with SCA enabled.
     * In this case we only want to display exercises with the given language
     */
    programmingLanguage = input<ProgrammingLanguage | undefined>();

    protected readonly selectedExerciseType = computed(() => (this.dialogConfig?.data as ExerciseImportDialogData | undefined)?.exerciseType ?? this.exerciseType());
    private readonly selectedProgrammingLanguage = computed(
        () => (this.dialogConfig?.data as ExerciseImportDialogData | undefined)?.programmingLanguage ?? this.programmingLanguage(),
    );

    readonly isCourseFilter = signal(true);
    readonly isExamFilter = signal(true);

    protected readonly faEye = faEye;
    /** A preview shows the questions of a quiz, which the other exercise types do not have. */
    protected readonly isPreviewAvailable = computed(() => this.selectedExerciseType() === ExerciseType.QUIZ);
    readonly preview = signal<ExerciseImportPreview | undefined>(undefined);
    readonly previewVisible = signal(false);
    /** The id of the exercise whose quiz is currently loaded for the preview. */
    readonly previewLoadingId = signal<number | undefined>(undefined);
    /** The button that opened the preview. It is disabled while the quiz loads, so the dialog cannot restore the focus to it on its own. */
    private previewTrigger?: HTMLElement;
    protected readonly previewTotalPoints = computed(() => (this.preview()?.quiz.quizQuestions ?? []).reduce((total, question) => total + (question.points ?? 0), 0));

    titleKey?: string;

    constructor() {
        // The exercise import component does not know yet which paging service to use
        // This gets determined based on the exercise type, which is not set when invoking the constructor
        // Therefore we temporally use this empty paging service which directly gets overwritten in ngOnInit().
        super(undefined);

        // The preview is a dialog of its own on top of the PrimeNG import dialog, which closes on every Escape press at document level.
        // Handle Escape for the preview first, so that it closes only the preview and the import dialog stays open.
        effect((onCleanup) => {
            if (!this.previewVisible()) {
                return;
            }
            const closePreviewOnEscape = (event: KeyboardEvent) => {
                if (event.key === 'Escape') {
                    event.stopPropagation();
                    this.previewVisible.set(false);
                }
            };
            // eslint-disable-next-line localRules/enforce-cleanup-on-destroy -- removed via the effect's onCleanup, which runs on destroy
            this.document.addEventListener('keydown', closePreviewOnEscape, true);
            onCleanup(() => this.document.removeEventListener('keydown', closePreviewOnEscape, true));
        });
    }

    override ngOnInit(): void {
        const exerciseType = this.selectedExerciseType();
        if (!exerciseType) {
            return;
        }

        const programmingLanguage = this.selectedProgrammingLanguage();
        this.pagingService = this.getPagingService(exerciseType, programmingLanguage);
        if (programmingLanguage) {
            this.titleKey = 'artemisApp.programmingExercise.configureGrading.categories.importLabel';
        } else {
            this.titleKey = exerciseType === ExerciseType.FILE_UPLOAD ? `artemisApp.fileUploadExercise.home.importLabel` : `artemisApp.${exerciseType}Exercise.home.importLabel`;
        }

        super.ngOnInit();
        this.search.next();
    }

    private getPagingService(exerciseType: ExerciseType, programmingLanguage?: ProgrammingLanguage): ExercisePagingService<Exercise> {
        switch (exerciseType) {
            case ExerciseType.MODELING:
                return this.injector.get(ModelingExercisePagingService);
            case ExerciseType.PROGRAMMING:
                if (programmingLanguage) {
                    return this.injector.get(CodeAnalysisPagingService);
                }
                return this.injector.get(ProgrammingExercisePagingService);
            case ExerciseType.QUIZ:
                return this.injector.get(QuizExercisePagingService);
            case ExerciseType.TEXT:
                return this.injector.get(TextExercisePagingService);
            case ExerciseType.FILE_UPLOAD:
                return this.injector.get(FileUploadExercisePagingService);
            default:
                throw new Error('Unsupported exercise type: ' + String(exerciseType));
        }
    }

    protected override createOptions(): object {
        return { isCourseFilter: this.isCourseFilter(), isExamFilter: this.isExamFilter(), programmingLanguage: this.selectedProgrammingLanguage() };
    }

    override set sortedColumn(sortedColumn: string) {
        if (sortedColumn === 'COURSE_TITLE') {
            if (this.isExamFilter() && !this.isCourseFilter()) {
                sortedColumn = 'EXAM_TITLE';
            }
            // sort by course / exam title is not possible if course and exam exercises are mixed
        }
        this.setSearchParam({ sortedColumn });
    }

    // When overriding the setter, we also need to override the getter.
    // Otherwise typescript will always return undefined when using the getter.
    override get sortedColumn(): string {
        return this.state.sortedColumn;
    }

    onCourseFilterChange() {
        this.isCourseFilter.update((value) => !value);
        this.resetSortOnFilterChange();
        this.search.next();
    }

    onExamFilterChange() {
        this.isExamFilter.update((value) => !value);
        this.resetSortOnFilterChange();
        this.search.next();
    }

    // reset to default search option when mixing course and exam exercises.
    // This avoids exercises still being filtered out by the sortedColum even if the filter is not set.
    private resetSortOnFilterChange() {
        if (this.sortedColumn === 'COURSE_TITLE' || this.sortedColumn === 'EXAM_TITLE') {
            this.sortedColumn = DEFAULT_SORT_COLUMN;
        }
    }

    /**
     * Loads the complete quiz of the given row, which the paged search does not contain, and shows it in the preview dialog.
     * Only one quiz is loaded at a time.
     *
     * @param exercise the row the user wants to preview
     * @param event    the click on the preview button of the row, whose button gets the focus back when the preview is closed
     */
    openPreview(exercise: Exercise, event?: MouseEvent): void {
        if (exercise.id === undefined || this.previewLoadingId() !== undefined) {
            return;
        }
        this.previewTrigger = event?.currentTarget instanceof HTMLElement ? event.currentTarget : undefined;
        this.previewLoadingId.set(exercise.id);
        this.quizExerciseService
            .find(exercise.id)
            .pipe(takeUntilDestroyed(this.previewDestroyRef))
            .subscribe({
                next: (response) => {
                    this.previewLoadingId.set(undefined);
                    if (response.body) {
                        this.preview.set({ exercise, quiz: response.body });
                        this.previewVisible.set(true);
                    } else {
                        this.alertService.error('artemisApp.exercise.import.preview.loadFailed');
                    }
                },
                error: () => {
                    this.previewLoadingId.set(undefined);
                    this.alertService.error('artemisApp.exercise.import.preview.loadFailed');
                },
            });
    }

    /**
     * Forgets the previewed quiz once its dialog is gone and gives the focus back to the button that opened it.
     * The dialog restores the focus on its own only to what was focused when it opened, and that is no longer the button.
     */
    protected onPreviewHidden(): void {
        this.preview.set(undefined);
        this.previewTrigger?.focus();
        this.previewTrigger = undefined;
    }

    /**
     * Imports the previewed exercise, as if the user had pressed the import button of its row.
     */
    importPreviewed(): void {
        const preview = this.preview();
        if (!preview) {
            return;
        }
        this.previewVisible.set(false);
        this.selectImport(preview.exercise);
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise;
    }
}
