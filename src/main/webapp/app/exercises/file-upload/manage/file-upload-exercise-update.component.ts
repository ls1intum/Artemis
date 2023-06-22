import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, ExerciseMode, IncludedInOverallScore, getCourseId, resetDates } from 'app/entities/exercise.model';
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
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { switchMap, tap } from 'rxjs/operators';

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
    isImport: boolean;
    examCourseId?: number;

    saveCommand: SaveExerciseCommand<FileUploadExercise>;

    documentationType = DocumentationType.FileUpload;

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
        private exerciseGroupService: ExerciseGroupService,
    ) {}

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }
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
            this.examCourseId = getCourseId(fileUploadExercise);
        });

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldHaveBackButtonToWizard) {
                this.goBackAfterSaving = true;
            }
        });
        this.activatedRoute.url
            .pipe(
                tap(
                    (segments) =>
                        (this.isImport = segments.some((segment) => segment.path === 'import', (this.isExamMode = segments.some((segment) => segment.path === 'exercise-groups')))),
                ),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    if (!this.isExamMode) {
                        if (this.fileUploadExercise.id == undefined && this.fileUploadExercise.channelName == undefined) {
                            this.fileUploadExercise.channelName = '';
                        }
                    }
                    this.handleExerciseSettings();
                    this.handleImport(params);
                }),
            )
            .subscribe();
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.fileUploadExercise);
    }

    private handleImport(params: Params) {
        if (this.isImport) {
            if (this.isExamMode) {
                // The target exerciseId where we want to import into
                const exerciseGroupId = params['exerciseGroupId'];
                const courseId = params['courseId'];
                const examId = params['examId'];

                this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.fileUploadExercise.exerciseGroup = res.body!));
                // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                this.fileUploadExercise.course = undefined;
            } else {
                // The target course where we want to import into
                const targetCourseId = params['courseId'];
                this.courseService.find(targetCourseId).subscribe((res) => (this.fileUploadExercise.course = res.body!));
                // We reference normal exercises by their course, having both would lead to conflicts on the server
                this.fileUploadExercise.exerciseGroup = undefined;
            }
            resetDates(this.fileUploadExercise);
        }
    }

    private handleExerciseSettings() {
        if (!this.isExamMode) {
            this.exerciseCategories = this.fileUploadExercise.categories || [];
            if (this.examCourseId) {
                this.courseService.findAllCategoriesOfCourse(this.examCourseId).subscribe({
                    next: (categoryRes: HttpResponse<string[]>) => {
                        this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            }
        } else {
            // Lock individual mode for exam exercises
            this.fileUploadExercise.mode = ExerciseMode.INDIVIDUAL;
            this.fileUploadExercise.teamAssignmentConfig = undefined;
            this.fileUploadExercise.teamMode = false;
            // Exam exercises cannot be not included into the total score
            if (this.fileUploadExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                this.fileUploadExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            }
        }
    }

    save() {
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.fileUploadExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.fileUploadExercise, this.isExamMode, this.notificationText)
            .subscribe({
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
        if (error.error && error.error.title) {
            this.alertService.addErrorAlert(error.error.title, error.error.message, error.error.params);
        }
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSaving = false;
    }
}
