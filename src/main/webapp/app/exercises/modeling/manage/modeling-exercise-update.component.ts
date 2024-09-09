import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseMode, IncludedInOverallScore, resetForImport } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep, isEmpty } from 'lodash-es';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise.utils';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from '../shared/modeling-editor.component';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { loadCourseExerciseCategories } from 'app/exercises/shared/course-exercises/course-utils';
import { FormSectionStatus } from 'app/forms/form-status-bar/form-status-bar.component';
import { Subscription } from 'rxjs';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { NgModel } from '@angular/forms';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModelingExerciseUpdateComponent implements AfterViewInit, OnDestroy, OnInit {
    @ViewChild(ExerciseTitleChannelNameComponent) exerciseTitleChannelNameComponent: ExerciseTitleChannelNameComponent;
    @ViewChild(ExerciseUpdatePlagiarismComponent) exerciseUpdatePlagiarismComponent?: ExerciseUpdatePlagiarismComponent;
    @ViewChild(TeamConfigFormGroupComponent) teamConfigFormGroupComponent?: TeamConfigFormGroupComponent;
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor?: ModelingEditorComponent;
    @ViewChild('bonusPoints') bonusPoints?: NgModel;
    @ViewChild('points') points?: NgModel;
    @ViewChild('solutionPublicationDate') solutionPublicationDateField?: FormDateTimePickerComponent;
    @ViewChild('releaseDate') releaseDateField?: FormDateTimePickerComponent;
    @ViewChild('startDate') startDateField?: FormDateTimePickerComponent;
    @ViewChild('dueDate') dueDateField?: FormDateTimePickerComponent;
    @ViewChild('assessmentDueDate') assessmentDateField?: FormDateTimePickerComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly documentationType: DocumentationType = 'Model';

    AssessmentType = AssessmentType;
    UMLDiagramType = UMLDiagramType;

    modelingExercise: ModelingExercise;
    backupExercise: ModelingExercise;
    exampleSolution: UMLModel;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;
    domainActionsProblemStatement = [new MonacoFormulaAction()];
    domainActionsExampleSolution = [new MonacoFormulaAction()];
    examCourseId?: number;
    isImport: boolean;
    isExamMode: boolean;
    semiAutomaticAssessmentAvailable = true;
    goBackAfterSaving = false;

    formSectionStatus: FormSectionStatus[];

    // Subscription
    titleChannelNameComponentSubscription?: Subscription;
    pointsSubscription?: Subscription;
    bonusPointsSubscription?: Subscription;
    plagiarismSubscription?: Subscription;
    teamSubscription?: Subscription;

    constructor(
        private alertService: AlertService,
        private modelingExerciseService: ModelingExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private exerciseGroupService: ExerciseGroupService,
        private eventManager: EventManager,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private navigationUtilService: ArtemisNavigationUtilService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.modelingExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    ngAfterViewInit() {
        this.titleChannelNameComponentSubscription = this.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValidChanges.subscribe(() =>
            this.calculateFormSectionStatus(),
        );
        this.pointsSubscription = this.points?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.bonusPointsSubscription = this.bonusPoints?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.plagiarismSubscription = this.exerciseUpdatePlagiarismComponent?.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
        this.teamSubscription = this.teamConfigFormGroupComponent?.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
    }

    /**
     * Initializes all relevant data for creating or editing modeling exercise
     */
    ngOnInit(): void {
        scrollToTopOfPage();

        // Get the modelingExercise
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;

            if (this.modelingExercise.exampleSolutionModel != undefined) {
                this.exampleSolution = JSON.parse(this.modelingExercise.exampleSolutionModel);
            }

            this.backupExercise = cloneDeep(this.modelingExercise);
            this.examCourseId = this.modelingExercise.course?.id || this.modelingExercise.exerciseGroup?.exam?.course?.id;
        });

        this.activatedRoute.url
            .pipe(
                tap(
                    (segments) =>
                        (this.isImport = segments.some((segment) => segment.path === 'import', (this.isExamMode = segments.some((segment) => segment.path === 'exercise-groups')))),
                ),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    let courseId;

                    if (!this.isExamMode) {
                        this.exerciseCategories = this.modelingExercise.categories || [];
                        if (this.modelingExercise.course) {
                            courseId = this.modelingExercise.course!.id!;
                        } else {
                            courseId = this.modelingExercise.exerciseGroup!.exam!.course!.id!;
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.modelingExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.modelingExercise.teamAssignmentConfig = undefined;
                        this.modelingExercise.teamMode = false;
                        // Exam exercises cannot be not included into the total score
                        if (this.modelingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                            this.modelingExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                        }
                    }
                    if (this.isImport) {
                        // The target course where we want to import into
                        courseId = params['courseId'];

                        if (this.isExamMode) {
                            // The target exerciseGroupId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.modelingExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.modelingExercise.course = undefined;
                        } else {
                            this.courseService.find(courseId).subscribe((res) => (this.modelingExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.modelingExercise.exerciseGroup = undefined;
                        }
                        resetForImport(this.modelingExercise);
                    }

                    loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
                        this.existingCategories = existingCategories;
                    });
                }),
            )
            .subscribe();

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldHaveBackButtonToWizard) {
                this.goBackAfterSaving = true;
            }
        });

        this.isSaving = false;
        this.notificationText = undefined;
    }

    ngOnDestroy() {
        this.titleChannelNameComponentSubscription?.unsubscribe();
        this.pointsSubscription?.unsubscribe();
        this.bonusPointsSubscription?.unsubscribe();
        this.plagiarismSubscription?.unsubscribe();
    }

    async calculateFormSectionStatus() {
        await this.modelingEditor?.apollonEditor?.nextRender;
        this.formSectionStatus = [
            {
                title: 'artemisApp.exercise.sections.general',
                valid: Boolean(this.exerciseTitleChannelNameComponent?.titleChannelNameComponent.formValid),
            },
            { title: 'artemisApp.exercise.sections.mode', valid: Boolean(this.teamConfigFormGroupComponent?.formValid) },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !this.modelingExercise.problemStatement },
            {
                title: 'artemisApp.exercise.sections.solution',
                valid: Boolean(this.isExamMode || (!this.modelingExercise.exampleSolutionPublicationDateError && this.solutionPublicationDateField?.dateInput.valid)),
                empty:
                    isEmpty(this.modelingEditor?.getCurrentModel()?.elements) ||
                    (!this.isExamMode && !this.modelingExercise.exampleSolutionPublicationDate) ||
                    !this.modelingExercise.exampleSolutionExplanation,
            },
            {
                title: 'artemisApp.exercise.sections.grading',
                valid: Boolean(
                    this.points?.valid &&
                        this.bonusPoints?.valid &&
                        (this.isExamMode ||
                            (this.exerciseUpdatePlagiarismComponent?.formValid &&
                                !this.modelingExercise.startDateError &&
                                !this.modelingExercise.dueDateError &&
                                !this.modelingExercise.assessmentDueDateError &&
                                this.releaseDateField?.dateInput.valid &&
                                this.startDateField?.dateInput.valid &&
                                this.dueDateField?.dateInput.valid &&
                                this.assessmentDateField?.dateInput.valid)),
                ),
                empty:
                    !this.isExamMode &&
                    // if a dayjs object contains an empty date, it is considered "invalid"
                    (!this.modelingExercise.startDate?.isValid() ||
                        !this.modelingExercise.dueDate?.isValid() ||
                        !this.modelingExercise.assessmentDueDate?.isValid() ||
                        !this.modelingExercise.releaseDate?.isValid()),
            },
        ];

        // otherwise the change detection does not work on the initial load
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]): void {
        this.modelingExercise.categories = categories;
        this.exerciseCategories = categories;
    }

    /**
     * Validates if the date is correct
     */
    validateDate(): void {
        this.exerciseService.validateDate(this.modelingExercise);
        this.calculateFormSectionStatus();
    }

    save() {
        this.modelingExercise.exampleSolutionModel = JSON.stringify(this.modelingEditor?.getCurrentModel());
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.modelingExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.modelingExercise, this.isExamMode, this.notificationText)
            .subscribe({
                next: (exercise: ModelingExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving = false;
                },
            });
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.modelingExercise);
    }

    private onSaveSuccess(exercise: ModelingExercise): void {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;

        if (this.goBackAfterSaving) {
            this.navigationUtilService.navigateBack();

            return;
        }

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(errorRes: HttpErrorResponse): void {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving = false;
    }

    /**
     * When the diagram type changes, we need to check whether {@link AssessmentType.SEMI_AUTOMATIC} is available for the type. If not, we revert to {@link AssessmentType.MANUAL}
     */
    diagramTypeChanged() {
        if (!this.semiAutomaticAssessmentAvailable) {
            this.modelingExercise.assessmentType = AssessmentType.MANUAL;
        }
    }
}
