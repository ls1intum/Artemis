import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, inject, input, output, signal, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiInputDirective,
    TumAetUiPaginatorComponent,
    TumAetUiTableDirective,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
} from '@tumaet/ui-angular';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ImportComponent } from 'app/shared-ui/import/import.component';
import { onError } from 'app/foundation/util/global.utils';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ExamImportProgressDialogComponent } from 'app/exam/manage/exams/exam-import/exam-import-progress-dialog.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';

@Component({
    selector: 'jhi-exam-import',
    templateUrl: './exam-import.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiPaginatorComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        ExamExerciseImportComponent,
        ExamImportProgressDialogComponent,
        ExamModeBadgeComponent,
    ],
})
export class ExamImportComponent extends ImportComponent<Exam> {
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);

    examImportProgressDialog = viewChild.required(ExamImportProgressDialogComponent);

    // Whether the import also includes the subsequent selection of the exercise groups of the chosen exam.
    readonly subsequentExerciseGroupSelection = input(false);
    // The target of the exercise group import
    readonly targetCourseId = input<number>();
    readonly targetExamId = input<number>();

    /** Emits the exam the user chose to import, if the exercise groups are not selected subsequently. */
    readonly examSelected = output<Exam>();
    /** Emits all exercise groups of the target exam after the exercise groups were imported. */
    readonly imported = output<ExerciseGroup[]>();

    examExerciseImportComponent = viewChild.required(ExamExerciseImportComponent);

    protected readonly faSpinner = faSpinner;

    readonly exam = signal<Exam | undefined>(undefined);
    readonly isImportingExercises = signal(false);
    readonly isImportInSameCourse = signal(false);

    constructor() {
        const pagingService = inject(ExamImportPagingService);
        super(pagingService);
    }

    override selectImport(exam: Exam) {
        this.examSelected.emit(exam);
    }

    /**
     * Applies the column sorting requested by the table header
     * @param event the column and direction requested by the user
     */
    onSortChange(event: TumAetUiTableSortEvent) {
        this.setSearchParam({ sortedColumn: event.field, sortingOrder: event.order > 0 ? SortingOrder.ASCENDING : SortingOrder.DESCENDING });
        this.sortRows();
    }

    /**
     * After the user has chosen an Exam, this method is called to load the exercise groups for the selected exam
     * @param exam the exam for which the exercise groups should be loaded
     */
    openExerciseSelection(exam: Exam) {
        this.examManagementService.findWithExercisesAndWithoutCourseId(exam.id!).subscribe({
            next: (examRes: HttpResponse<Exam>) => {
                const loadedExam = examRes.body!;
                this.exam.set(loadedExam);
                this.isImportInSameCourse.set(loadedExam.course?.id === this.targetCourseId());
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Method to map the Map<ExerciseGroup, Set<Exercises>> selectedExercises to an ExerciseGroup[] with Exercises[] each
     * and to perform the REST-Call to import the ExerciseGroups to the specified exam.
     * Called once when user is importing the exam
     */
    performImportOfExerciseGroups() {
        const currentExam = this.exam();
        if (this.subsequentExerciseGroupSelection() && currentExam && this.targetExamId() && this.targetCourseId()) {
            // The validation of the selected exercises is only called when the user desires to import the exam
            if (!this.examExerciseImportComponent().validateUserInput()) {
                this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidExerciseConfiguration');
                return;
            }
            this.isImportingExercises.set(true);
            // The child component provides us with the selected exercise groups and exercises
            const exerciseGroups = this.examExerciseImportComponent().mapSelectedExercisesToExerciseGroups();
            this.exam.set(cloneWith(currentExam, { exerciseGroups }));
            // Run the import behind a progress dialog that shows live websocket progress and a persistent, must-dismiss
            // summary of any skipped or incomplete exercises (so the editor cannot overlook them).
            const totalExercises = (exerciseGroups ?? []).reduce((sum, group) => sum + (group.exercises?.length ?? 0), 0);
            const importId = this.examManagementService.generateImportId();
            const request$ = this.examManagementService.importExerciseGroup(this.targetCourseId()!, this.targetExamId()!, exerciseGroups, importId);
            this.examImportProgressDialog()
                .runImport(importId, totalExercises, request$)
                .then((response: HttpResponse<ExerciseGroupImportResultDTO>) => {
                    this.isImportingExercises.set(false);
                    // Close-Variant 2: Provide the component with all the exercise groups and exercises of the exam
                    this.imported.emit(response.body?.exerciseGroups ?? []);
                })
                .catch((httpErrorResponse: HttpErrorResponse) => {
                    // Case: Server-Site Validation of the Programming Exercises failed
                    const errorKey = httpErrorResponse.error?.errorKey;
                    if (errorKey === 'invalidKey') {
                        // The Server sends back all the exercise groups and exercises and removed the shortName / title for all conflicting programming exercises
                        this.exam.update((exam) => cloneWith(exam!, { exerciseGroups: httpErrorResponse.error.params.exerciseGroups! }));
                        // Pass the returned groups explicitly: the child's exam input updates on the next change detection pass.
                        this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToInvalidProjectKey(this.exam()!.exerciseGroups);
                        const numberOfInvalidProgrammingExercises = httpErrorResponse.error.numberOfInvalidProgrammingExercises;
                        this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: numberOfInvalidProgrammingExercises });
                    } else if (errorKey === 'duplicatedProgrammingExerciseShortName' || errorKey === 'duplicatedProgrammingExerciseTitle') {
                        this.exam.update((exam) => cloneWith(exam!, { exerciseGroups: httpErrorResponse.error.params.exerciseGroups! }));
                        this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToDuplicatedShortNameOrTitle(this.exam()!.exerciseGroups);
                        this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
                    } else {
                        onError(this.alertService, httpErrorResponse);
                    }
                    this.isImportingExercises.set(false);
                });
        }
    }

    protected override createOptions(): object {
        return { withExercises: this.subsequentExerciseGroupSelection() };
    }
}
