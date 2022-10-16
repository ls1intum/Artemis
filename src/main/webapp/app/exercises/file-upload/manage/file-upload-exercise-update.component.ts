import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise.utils';
import { faBan, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html',
    styleUrls: ['../../shared/exercise/_exercise-update.scss'],
})
export class FileUploadExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    isExamMode: boolean;
    fileUploadExercise: FileUploadExercise;
    backupExercise: FileUploadExercise;
    isSaving: boolean;
    goBackAfterSaving = false;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    EditorMode = EditorMode;
    notificationText?: string;
    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];

    saveCommand: SaveExerciseCommand<FileUploadExercise>;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faBan = faBan;
    faSave = faSave;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private alertService: AlertService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    get editType(): EditType {
        return this.fileUploadExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Initializes information relevant to file upload exercise
     */
    ngOnInit() {
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
                this.courseService.findAllCategoriesOfCourse(this.fileUploadExercise.course!.id!).subscribe({
                    next: (categoryRes: HttpResponse<string[]>) => {
                        this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            }
            // Exam exercises cannot be not included into the total score
            if (this.isExamMode && this.fileUploadExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                this.fileUploadExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            }

            this.saveCommand = new SaveExerciseCommand(this.modalService, this.popupService, this.fileUploadExerciseService, this.backupExercise, this.editType, this.alertService);
        });

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldHaveBackButtonToWizard) {
                this.goBackAfterSaving = true;
            }
        });
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.fileUploadExercise);
    }

    save() {
        this.isSaving = true;

        this.saveCommand.save(this.fileUploadExercise, this.notificationText).subscribe({
            next: (exercise: Exercise) => this.onSaveSuccess(exercise),
            error: (res: HttpErrorResponse) => this.onSaveError(res),
            complete: () => {
                this.isSaving = false;
            },
        });
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.fileUploadExercise);
    }
    /**
     * Updates categories for file upload exercise
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.fileUploadExercise.categories = categories;
    }

    private onSaveSuccess(exercise: Exercise) {
        this.isSaving = false;

        if (this.goBackAfterSaving) {
            this.navigationUtilService.navigateBack();

            return;
        }

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSaving = false;
    }
}
