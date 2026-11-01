import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { FileUploadExercisePagingService } from 'app/fileupload/manage/services/file-upload-exercise-paging.service';
import { ModelingExercisePagingService } from 'app/modeling/manage/services/modeling-exercise-paging.service';
import { CodeAnalysisPagingService } from 'app/programming/manage/services/code-analysis-paging.service';
import { ProgrammingExercisePagingService } from 'app/programming/manage/services/programming-exercise-paging.service';
import { QuizExercisePagingService } from 'app/quiz/manage/service/quiz-exercise-paging.service';
import { PagingService } from 'app/exercise/services/paging.service';
import { TextExercisePagingService } from 'app/text/manage/text-exercise/service/text-exercise-paging.service';
import { ImportComponent } from 'app/shared-ui/import/import.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiCheckboxComponent, TumAetUiInputDirective, TumAetUiPaginatorComponent, TumAetUiTableDirective } from '@tumaet/ui-angular';
import { ExerciseCourseTitlePipe } from 'app/foundation/pipes/exercise-course-title.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { faArrowLeft, faEye } from '@fortawesome/free-solid-svg-icons';
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
        TumAetUiCheckboxComponent,
        TumAetUiInputDirective,
        TumAetUiPaginatorComponent,
        TumAetUiTableDirective,
        ExerciseCourseTitlePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
        TumAetUiButtonComponent,
        TumAetUiTooltipDirective,
        QuizQuestionPreviewComponent,
    ],
})
export class ExerciseImportComponent extends ImportComponent<Exercise> implements OnInit {
    private injector = inject(Injector);
    private quizExerciseService = inject(QuizExerciseService);
    private alertService = inject(AlertService);
    private previewDestroyRef = inject(DestroyRef);
    private host = inject<ElementRef<HTMLElement>>(ElementRef);
    private document = inject(DOCUMENT);
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
    protected readonly faArrowLeft = faArrowLeft;
    /** A preview shows the questions of a quiz, which the other exercise types do not have. */
    protected readonly isPreviewAvailable = computed(() => this.selectedExerciseType() === ExerciseType.QUIZ);
    /** The quiz shown instead of the list, if any. */
    readonly preview = signal<ExerciseImportPreview | undefined>(undefined);
    /** The id of the exercise whose quiz is currently loaded for the preview. */
    readonly previewLoadingId = signal<number | undefined>(undefined);
    /** The button that opened the preview, which gets the focus back when the preview is closed. */
    private previewTrigger?: HTMLElement;
    private readonly previewHeading = viewChild('previewHeading', { read: ElementRef<HTMLElement> });
    protected readonly previewTotalPoints = computed(() => (this.preview()?.quiz.quizQuestions ?? []).reduce((total, question) => total + (question.points ?? 0), 0));

    titleKey?: string;

    constructor() {
        // The exercise import component does not know yet which paging service to use
        // This gets determined based on the exercise type, which is not set when invoking the constructor
        // Therefore we temporally use this empty paging service which directly gets overwritten in ngOnInit().
        super(undefined);

        // Escape closes the dialog that hosts this component, whichever of its controls has the focus. While the preview is shown,
        // it leads back to the list instead. The capture phase of the document sees the key before the handler of the dialog does.
        const onKeydown = (event: KeyboardEvent) => this.onPreviewEscape(event);
        this.document.addEventListener('keydown', onKeydown, true);
        this.previewDestroyRef.onDestroy(() => this.document.removeEventListener('keydown', onKeydown, true));
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

    private getPagingService(exerciseType: ExerciseType, programmingLanguage?: ProgrammingLanguage): PagingService<Exercise> {
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
     * Loads the complete quiz of the given row, which the paged search does not contain, and shows it instead of the list.
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
        (exercise.exerciseGroup ? this.quizExerciseService.findForExam(exercise.id) : this.quizExerciseService.find(exercise.id))
            .pipe(takeUntilDestroyed(this.previewDestroyRef))
            .subscribe({
                next: (quiz) => {
                    this.previewLoadingId.set(undefined);
                    this.preview.set({ exercise, quiz });
                    // The heading exists after the next render. Moving the focus there keeps it from staying on the now hidden row.
                    afterNextRender(() => this.previewHeading()?.nativeElement.focus(), { injector: this.injector });
                },
                error: () => {
                    this.previewLoadingId.set(undefined);
                    this.alertService.error('artemisApp.exercise.import.preview.loadFailed');
                    // The button was disabled while loading, which moved the focus away from it.
                    this.restoreFocusToList();
                },
            });
    }

    /**
     * Shows the list again and gives the focus back to the button that opened the preview.
     */
    protected closePreview(): void {
        this.preview.set(undefined);
        // The list is only visible after the next render, and an element of a hidden list cannot take the focus.
        this.restoreFocusToList();
    }

    /**
     * Keeps Escape from closing the import dialog while the preview is shown, and shows the list instead.
     * Without a preview, Escape is left to the dialog that hosts this component.
     *
     * @param event the keydown event, which only matters if it is the Escape key
     */
    private onPreviewEscape(event: KeyboardEvent): void {
        if (!this.preview() || event.key !== 'Escape' || event.isComposing) {
            return;
        }
        event.stopPropagation();
        this.closePreview();
    }

    /**
     * Focuses the preview button that was clicked last. The row of that button is gone if the list has changed in the meantime,
     * so the first preview button of the list takes the focus then.
     */
    private restoreFocusToList(): void {
        const trigger = this.previewTrigger;
        this.previewTrigger = undefined;
        afterNextRender(
            () => {
                const target = trigger?.isConnected ? trigger : this.host.nativeElement.querySelector<HTMLElement>('[data-testid^="preview-exercise-"] button');
                target?.focus();
            },
            { injector: this.injector },
        );
    }

    /**
     * Imports the previewed exercise, as if the user had pressed the import button of its row.
     */
    importPreviewed(): void {
        const preview = this.preview();
        if (!preview) {
            return;
        }
        this.selectImport(preview.exercise);
    }

    highlightParts(value: string | undefined): { text: string; highlighted: boolean }[] {
        if (!value || !this.searchTerm) {
            return [{ text: value ?? '', highlighted: false }];
        }
        const parts: { text: string; highlighted: boolean }[] = [];
        const term = this.searchTerm.toLocaleLowerCase();
        let start = 0;
        let match = value.toLocaleLowerCase().indexOf(term);
        while (match !== -1) {
            parts.push({ text: value.slice(start, match), highlighted: false });
            parts.push({ text: value.slice(match, match + term.length), highlighted: true });
            start = match + term.length;
            match = value.toLocaleLowerCase().indexOf(term, start);
        }
        parts.push({ text: value.slice(start), highlighted: false });
        return parts;
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise;
    }
}
