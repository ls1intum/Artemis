import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, inject, input, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Component, Input, ViewChild, inject } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { Exam } from 'app/entities/exam/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ImportComponent } from 'app/shared/import/import.component';
import { onError } from 'app/shared/util/global.utils';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbHighlight, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-exam-import',
    templateUrl: './exam-import.component.html',
    imports: [FormsModule, TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgbHighlight, ButtonComponent, NgbPagination, ExamExerciseImportComponent],
})
export class ExamImportComponent extends ImportComponent<Exam> {
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);

    // boolean to indicate, if the import modal should include the exerciseGroup selection subsequently.
    subsequentExerciseGroupSelection = input<boolean>();
    // Values to specify the target of the exercise group import
    targetCourseId = input<number>();
    targetExamId = input<number>();

    examExerciseImportComponent = viewChild.required(ExamExerciseImportComponent);

    exam?: Exam;
    isImportingExercises = false;
    isImportInSameCourse = false;

    constructor() {
        const pagingService = inject(ExamImportPagingService);
        super(pagingService);
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
                    this.activeModal.close(httpResponse.body!);
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
