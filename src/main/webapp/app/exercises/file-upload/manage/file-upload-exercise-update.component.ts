import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, ExerciseMode, IncludedInOverallScore, getCourseId, resetForImport } from 'app/entities/exercise.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { switchMap, tap } from 'rxjs/operators';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { NgModel } from '@angular/forms';
import { Subscription } from 'rxjs';
import { FormSectionStatus } from 'app/forms/form-status-bar/form-status-bar.component';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileUploadExerciseUpdateComponent implements AfterViewInit, OnDestroy, OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly documentationType: DocumentationType = 'FileUpload';

    @ViewChild('bonusPoints') bonusPoints: NgModel;
    @ViewChild('points') points: NgModel;
    @ViewChild('solutionPublicationDate') solutionPublicationDateField?: FormDateTimePickerComponent;
    @ViewChild('releaseDate') releaseDateField?: FormDateTimePickerComponent;
    @ViewChild('startDate') startDateField?: FormDateTimePickerComponent;
    @ViewChild('dueDate') dueDateField?: FormDateTimePickerComponent;
    @ViewChild('assessmentDueDate') assessmentDateField?: FormDateTimePickerComponent;
    @ViewChild(ExerciseTitleChannelNameComponent) exerciseTitleChannelNameComponent: ExerciseTitleChannelNameComponent;
    @ViewChild(TeamConfigFormGroupComponent) teamConfigFormGroupComponent: TeamConfigFormGroupComponent;

    isExamMode: boolean;
    fileUploadExercise: FileUploadExercise;
    backupExercise: FileUploadExercise;
    isSaving: boolean;
    goBackAfterSaving = false;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;
    domainActionsProblemStatement = [new MonacoFormulaAction()];
    domainActionsExampleSolution = [new MonacoFormulaAction()];
    isImport: boolean;
    examCourseId?: number;

    saveCommand: SaveExerciseCommand<FileUploadExercise>;

    formStatusSections: FormSectionStatus[];

    // Subcriptions
    titleChannelNameComponentSubscription?: Subscription;
    pointsSubscription?: Subscription;
    bonusPointsSubscription?: Subscription;
    teamSubscription?: Subscription;

    // Icons
    faQuestionCircle = faQuestionCircle;

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private activatedRoute: ActivatedRoute,
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
        scrollToTopOfPage();

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
                    this.handleExerciseSettings();
                    this.handleImport(params);
                }),
            )
            .subscribe();
    }

    ngAfterViewInit() {
        this.titleChannelNameComponentSubscription = this.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValidChanges.subscribe(() =>
            this.calculateFormSectionStatus(),
        );
        this.pointsSubscription = this.points?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.bonusPointsSubscription = this.bonusPoints?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.teamSubscription = this.teamConfigFormGroupComponent.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
    }

    ngOnDestroy() {
        this.titleChannelNameComponentSubscription?.unsubscribe();
        this.pointsSubscription?.unsubscribe();
        this.bonusPointsSubscription?.unsubscribe();
        this.teamSubscription?.unsubscribe();
    }

    calculateFormSectionStatus() {
        this.formStatusSections = [
            {
                title: 'artemisApp.exercise.sections.general',
                valid: this.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValid,
            },
            { title: 'artemisApp.exercise.sections.mode', valid: this.teamConfigFormGroupComponent.formValid },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !this.fileUploadExercise.problemStatement },
            {
                title: 'artemisApp.exercise.sections.solution',
                valid: Boolean(this.isExamMode || (!this.fileUploadExercise.exampleSolutionPublicationDateError && this.solutionPublicationDateField?.dateInput.valid)),
                empty: !this.fileUploadExercise.exampleSolution || (!this.isExamMode && !this.fileUploadExercise.exampleSolutionPublicationDate),
            },
            {
                title: 'artemisApp.exercise.sections.grading',
                valid: Boolean(
                    this.points.valid &&
                        this.bonusPoints.valid &&
                        (this.isExamMode ||
                            (!this.fileUploadExercise.startDateError &&
                                !this.fileUploadExercise.dueDateError &&
                                !this.fileUploadExercise.assessmentDueDateError &&
                                this.releaseDateField?.dateInput.valid &&
                                this.startDateField?.dateInput.valid &&
                                this.dueDateField?.dateInput.valid &&
                                this.assessmentDateField?.dateInput.valid)),
                ),
                empty:
                    !this.isExamMode &&
                    // if a dayjs object contains an empty date, it is considered "invalid"
                    (!this.fileUploadExercise.startDate?.isValid() ||
                        !this.fileUploadExercise.dueDate?.isValid() ||
                        !this.fileUploadExercise.assessmentDueDate?.isValid() ||
                        !this.fileUploadExercise.releaseDate?.isValid()),
            },
        ];
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
            resetForImport(this.fileUploadExercise);
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
        this.calculateFormSectionStatus();
    }

    /**
     * Updates categories for file upload exercise
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.fileUploadExercise.categories = categories;
        this.exerciseCategories = categories;
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
