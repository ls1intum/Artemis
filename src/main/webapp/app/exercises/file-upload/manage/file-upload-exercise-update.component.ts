import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { navigateBackFromExerciseUpdate } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise-utils';

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html',
    styleUrls: ['./file-upload-exercise-update.component.scss'],
})
export class FileUploadExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    checkedFlag: boolean;
    isExamMode: boolean;
    fileUploadExercise: FileUploadExercise;
    backupExercise: FileUploadExercise;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    EditorMode = EditorMode;
    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];

    saveCommand: SaveExerciseCommand<FileUploadExercise>;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private activatedRoute: ActivatedRoute,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
    ) {}

    get editType(): EditType {
        return this.fileUploadExercise.id === undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Initializes information relevant to file upload exercise
     */
    ngOnInit() {
        this.checkedFlag = false; // default value of grading instructions toggle

        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.
        window.scroll(0, 0);

        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ fileUploadExercise }) => {
            this.fileUploadExercise = fileUploadExercise;
            this.backupExercise = cloneDeep(this.fileUploadExercise);
            this.isExamMode = this.fileUploadExercise.exerciseGroup !== undefined;
            if (!this.isExamMode) {
                this.exerciseCategories = this.fileUploadExercise.categories || [];
                this.courseService.findAllCategoriesOfCourse(this.fileUploadExercise.course!.id!).subscribe(
                    (categoryRes: HttpResponse<string[]>) => {
                        this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                    },
                    (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                );
            }

            this.saveCommand = new SaveExerciseCommand(this.modalService, this.popupService, this.fileUploadExerciseService, this.backupExercise, this.editType);
        });
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing exercise
     * Returns to the overview page if there is no previous state and we created a new exercise
     * Returns to the exercise group page if we are in exam mode
     */
    previousState() {
        navigateBackFromExerciseUpdate(this.router, this.fileUploadExercise);
    }

    save() {
        this.isSaving = true;

        this.saveCommand.save(this.fileUploadExercise).subscribe(
            () => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.fileUploadExercise);
    }
    /**
     * Updates categories for file upload exercise
     * @param categories list of exercies categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.fileUploadExercise.categories = categories;
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }
    /**
     * gets the flag of the structured grading instructions slide toggle
     */
    getCheckedFlag(event: boolean) {
        this.checkedFlag = event;
    }
}
