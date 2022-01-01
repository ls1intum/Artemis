import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
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
                this.courseService.findAllCategoriesOfCourse(this.fileUploadExercise.course!.id!).subscribe(
                    (categoryRes: HttpResponse<string[]>) => {
                        this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                    },
                    (error: HttpErrorResponse) => onError(this.alertService, error),
                );
            }

            this.saveCommand = new SaveExerciseCommand(this.modalService, this.popupService, this.fileUploadExerciseService, this.backupExercise, this.editType);
        });
    }

    /**
     * Return to the previous page or a default if no previous page exists
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.fileUploadExercise);
    }

    save() {
        this.isSaving = true;

        this.saveCommand.save(this.fileUploadExercise, this.notificationText).subscribe(
            () => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res),
            () => {
                this.isSaving = false;
            },
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
        const jhiAlert = this.alertService.error(errorMessage);
        jhiAlert.message = errorMessage;
        this.isSaving = false;
    }
}
