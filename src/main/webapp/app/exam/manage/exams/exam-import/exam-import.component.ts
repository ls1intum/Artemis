import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroupImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ImportComponent } from 'app/shared-ui/import/import.component';
import { onError } from 'app/foundation/util/global.utils';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ExamImportProgressDialogComponent } from 'app/exam/manage/exams/exam-import/exam-import-progress-dialog.component';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';

export interface ExamImportDialogData {
    subsequentExerciseGroupSelection?: boolean;
    targetCourseId?: number;
    targetExamId?: number;
}

@Component({
    selector: 'jhi-exam-import',
    templateUrl: './exam-import.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        SortDirective,
        SortByDirective,
        FaIconComponent,
        NgbHighlight,
        ButtonComponent,
        NgbPagination,
        ExamExerciseImportComponent,
        ExamImportProgressDialogComponent,
        ExamModeBadgeComponent,
    ],
})
export class ExamImportComponent extends ImportComponent<Exam> implements OnInit {
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);

    examImportProgressDialog = viewChild.required(ExamImportProgressDialogComponent);

    // boolean to indicate, if the import modal should include the exerciseGroup selection subsequently.
    subsequentExerciseGroupSelection = signal<boolean>(false);
    // Values to specify the target of the exercise group import
    targetCourseId = signal<number | undefined>(undefined);
    targetExamId = signal<number | undefined>(undefined);

    examExerciseImportComponent = viewChild.required(ExamExerciseImportComponent);

    readonly exam = signal<Exam | undefined>(undefined);
    readonly isImportingExercises = signal(false);
    readonly isImportInSameCourse = signal(false);

    constructor() {
        const pagingService = inject(ExamImportPagingService);
        super(pagingService);
    }

    override ngOnInit(): void {
        // Get data from DynamicDialogConfig if available (when opened via DialogService)
        const dialogData = this.dialogConfig?.data as ExamImportDialogData | undefined;
        if (dialogData) {
            if (dialogData.subsequentExerciseGroupSelection !== undefined) {
                this.subsequentExerciseGroupSelection.set(dialogData.subsequentExerciseGroupSelection);
            }
            if (dialogData.targetCourseId !== undefined) {
                this.targetCourseId.set(dialogData.targetCourseId);
            }
            if (dialogData.targetExamId !== undefined) {
                this.targetExamId.set(dialogData.targetExamId);
            }
        }

        super.ngOnInit();
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
            this.exam.set({ ...currentExam, exerciseGroups });
            // Run the import behind a progress dialog that shows live websocket progress and a persistent, must-dismiss
            // summary of any skipped or incomplete exercises (so the editor cannot overlook them).
            const totalExercises = (exerciseGroups ?? []).reduce((sum, group) => sum + (group.exercises?.length ?? 0), 0);
            const importId = this.examManagementService.generateImportId();
            const request$ = this.examManagementService.importExerciseGroup(this.targetCourseId()!, this.targetExamId()!, exerciseGroups!, importId);
            this.examImportProgressDialog()
                .runImport(importId, totalExercises, request$)
                .then((response: HttpResponse<ExerciseGroupImportResultDTO>) => {
                    this.isImportingExercises.set(false);
                    // Close-Variant 2: Provide the component with all the exercise groups and exercises of the exam
                    this.dialogRef?.close(response.body?.exerciseGroups ?? []);
                })
                .catch((httpErrorResponse: HttpErrorResponse) => {
                    // Case: Server-Site Validation of the Programming Exercises failed
                    const errorKey = httpErrorResponse.error?.errorKey;
                    if (errorKey === 'invalidKey') {
                        // The Server sends back all the exercise groups and exercises and removed the shortName / title for all conflicting programming exercises
                        this.exam.update((exam) => ({ ...exam!, exerciseGroups: httpErrorResponse.error.params.exerciseGroups! }));
                        // The updateMapsAfterRejectedImport Method is called to update the displayed exercises in the child component
                        this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToInvalidProjectKey();
                        const numberOfInvalidProgrammingExercises = httpErrorResponse.error.numberOfInvalidProgrammingExercises;
                        this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: numberOfInvalidProgrammingExercises });
                    } else if (errorKey === 'duplicatedProgrammingExerciseShortName' || errorKey === 'duplicatedProgrammingExerciseTitle') {
                        this.exam.update((exam) => ({ ...exam!, exerciseGroups: httpErrorResponse.error.params.exerciseGroups! }));
                        this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToDuplicatedShortNameOrTitle();
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
