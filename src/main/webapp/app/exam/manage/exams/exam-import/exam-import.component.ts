import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ImportComponent } from 'app/shared/import/import.component';
import { onError } from 'app/shared/util/global.utils';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';

export interface ExamImportDialogData {
    subsequentExerciseGroupSelection?: boolean;
    targetCourseId?: number;
    targetExamId?: number;
}

@Component({
    selector: 'jhi-exam-import',
    templateUrl: './exam-import.component.html',
    imports: [FormsModule, TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgbHighlight, ButtonComponent, NgbPagination, ExamExerciseImportComponent],
})
export class ExamImportComponent extends ImportComponent<Exam> implements OnInit {
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);

    // boolean to indicate, if the import modal should include the exerciseGroup selection subsequently.
    subsequentExerciseGroupSelection = signal<boolean>(false);
    // Values to specify the target of the exercise group import
    targetCourseId = signal<number | undefined>(undefined);
    targetExamId = signal<number | undefined>(undefined);

    examExerciseImportComponent = viewChild.required(ExamExerciseImportComponent);

    exam?: Exam;
    isImportingExercises = false;
    isImportInSameCourse = false;

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
                this.exam = examRes.body!;
                this.isImportInSameCourse = this.exam.course?.id === this.targetCourseId();
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
        if (this.subsequentExerciseGroupSelection() && this.exam && this.targetExamId() && this.targetCourseId()) {
            // The validation of the selected exercises is only called when the user desires to import the exam
            if (!this.examExerciseImportComponent().validateUserInput()) {
                this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidExerciseConfiguration');
                return;
            }
            this.isImportingExercises = true;
            // The child component provides us with the selected exercise groups and exercises
            this.exam.exerciseGroups = this.examExerciseImportComponent().mapSelectedExercisesToExerciseGroups();
            this.examManagementService.importExerciseGroup(this.targetCourseId()!, this.targetExamId()!, this.exam.exerciseGroups!).subscribe({
                next: (httpResponse: HttpResponse<ExerciseGroup[]>) => {
                    this.isImportingExercises = false;
                    // Close-Variant 2: Provide the component with all the exercise groups and exercises of the exam
                    this.dialogRef?.close(httpResponse.body!);
                },
                error: (httpErrorResponse: HttpErrorResponse) => {
                    // Case: Server-Site Validation of the Programming Exercises failed
                    const errorKey = httpErrorResponse.error?.errorKey;
                    if (errorKey === 'invalidKey') {
                        // The Server sends back all the exercise groups and exercises and removed the shortName / title for all conflicting programming exercises
                        this.exam!.exerciseGroups = httpErrorResponse.error.params.exerciseGroups!;
                        // The updateMapsAfterRejectedImport Method is called to update the displayed exercises in the child component
                        this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToInvalidProjectKey();
                        const numberOfInvalidProgrammingExercises = httpErrorResponse.error.numberOfInvalidProgrammingExercises;
                        this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: numberOfInvalidProgrammingExercises });
                    } else if (errorKey === 'duplicatedProgrammingExerciseShortName' || errorKey === 'duplicatedProgrammingExerciseTitle') {
                        this.exam!.exerciseGroups = httpErrorResponse.error.params.exerciseGroups!;
                        this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToDuplicatedShortNameOrTitle();
                        this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
                    } else {
                        onError(this.alertService, httpErrorResponse);
                    }
                    this.isImportingExercises = false;
                },
            });
        }
    }

    protected override createOptions(): object {
        return { withExercises: this.subsequentExerciseGroupSelection() };
    }
}
