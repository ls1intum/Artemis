import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseMode, IncludedInOverallScore, resetForImport } from 'app/entities/exercise.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { NgForm, NgModel } from '@angular/forms';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { AthenaService } from 'app/assessment/athena.service';
import { Observable, Subscription } from 'rxjs';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { loadCourseExerciseCategories } from 'app/exercises/shared/course-exercises/course-utils';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { FormSectionStatus } from 'app/forms/form-status-bar/form-status-bar.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TextExerciseUpdateComponent implements OnInit, OnDestroy, AfterViewInit {
    private alertService = inject(AlertService);
    private textExerciseService = inject(TextExerciseService);
    private modalService = inject(NgbModal);
    private popupService = inject(ExerciseUpdateWarningService);
    private exerciseService = inject(ExerciseService);
    private exerciseGroupService = inject(ExerciseGroupService);
    private courseService = inject(CourseManagementService);
    private eventManager = inject(EventManager);
    private activatedRoute = inject(ActivatedRoute);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private athenaService = inject(AthenaService);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly documentationType: DocumentationType = 'Text';

    @ViewChild('editForm') editForm: NgForm;
    @ViewChild('bonusPoints') bonusPoints: NgModel;
    @ViewChild('points') points: NgModel;
    @ViewChild('solutionPublicationDate') solutionPublicationDateField?: FormDateTimePickerComponent;
    @ViewChild('releaseDate') releaseDateField?: FormDateTimePickerComponent;
    @ViewChild('startDate') startDateField?: FormDateTimePickerComponent;
    @ViewChild('dueDate') dueDateField?: FormDateTimePickerComponent;
    @ViewChild('assessmentDueDate') assessmentDateField?: FormDateTimePickerComponent;
    @ViewChild(ExerciseTitleChannelNameComponent) exerciseTitleChannelNameComponent: ExerciseTitleChannelNameComponent;
    @ViewChild(ExerciseUpdatePlagiarismComponent) exerciseUpdatePlagiarismComponent?: ExerciseUpdatePlagiarismComponent;
    @ViewChild(TeamConfigFormGroupComponent) teamConfigFormGroupComponent: TeamConfigFormGroupComponent;

    examCourseId?: number;
    isExamMode: boolean;
    isImport = false;
    goBackAfterSaving = false;
    AssessmentType = AssessmentType;
    isAthenaEnabled$: Observable<boolean> | undefined;

    textExercise: TextExercise;
    backupExercise: TextExercise;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;

    domainActionsProblemStatement = [new FormulaAction()];
    domainActionsExampleSolution = [new FormulaAction()];

    formSectionStatus: FormSectionStatus[];

    // subcriptions
    titleChannelNameComponentSubscription?: Subscription;
    pointsSubscription?: Subscription;
    bonusPointsSubscription?: Subscription;
    plagiarismSubscription?: Subscription;
    teamSubscription?: Subscription;

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.textExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    ngAfterViewInit() {
        this.titleChannelNameComponentSubscription = this.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValidChanges.subscribe(() =>
            this.calculateFormSectionStatus(),
        );
        this.pointsSubscription = this.points?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.bonusPointsSubscription = this.bonusPoints?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.plagiarismSubscription = this.exerciseUpdatePlagiarismComponent?.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
        this.teamSubscription = this.teamConfigFormGroupComponent.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
    }

    /**
     * Initializes all relevant data for creating or editing text exercise
     */
    ngOnInit() {
        scrollToTopOfPage();

        // Get the textExercise
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;

            this.backupExercise = cloneDeep(this.textExercise);
            this.examCourseId = this.textExercise.course?.id || this.textExercise.exerciseGroup?.exam?.course?.id;
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
                        this.exerciseCategories = this.textExercise.categories || [];
                        if (this.examCourseId) {
                            this.loadCourseExerciseCategories(this.examCourseId);
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.textExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.textExercise.teamAssignmentConfig = undefined;
                        this.textExercise.teamMode = false;
                        // Exam exercises cannot be not included into the total score
                        if (this.textExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                            this.textExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                        }
                    }
                    if (this.isImport) {
                        const courseId = params['courseId'];

                        if (this.isExamMode) {
                            // The target exerciseId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.textExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.textExercise.course = undefined;
                        } else {
                            // The target course where we want to import into
                            this.courseService.find(courseId).subscribe((res) => (this.textExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.textExercise.exerciseGroup = undefined;
                        }

                        this.loadCourseExerciseCategories(courseId);
                        resetForImport(this.textExercise);
                    }
                }),
            )
            .subscribe();

        this.activatedRoute.queryParams.subscribe((params) => {
            if (params.shouldHaveBackButtonToWizard) {
                this.goBackAfterSaving = true;
            }
        });

        this.isAthenaEnabled$ = this.athenaService.isEnabled();

        this.isSaving = false;
        this.notificationText = undefined;
    }

    ngOnDestroy() {
        this.titleChannelNameComponentSubscription?.unsubscribe();
        this.pointsSubscription?.unsubscribe();
        this.bonusPointsSubscription?.unsubscribe();
        this.plagiarismSubscription?.unsubscribe();
    }

    calculateFormSectionStatus() {
        if (this.textExercise) {
            this.formSectionStatus = [
                {
                    title: 'artemisApp.exercise.sections.general',
                    valid: this.exerciseTitleChannelNameComponent.titleChannelNameComponent.formValid,
                },
                { title: 'artemisApp.exercise.sections.mode', valid: this.teamConfigFormGroupComponent.formValid },
                { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !this.textExercise.problemStatement },
                {
                    title: 'artemisApp.exercise.sections.solution',
                    valid: Boolean(this.isExamMode || (!this.textExercise.exampleSolutionPublicationDateError && this.solutionPublicationDateField?.dateInput.valid)),
                    empty: !this.textExercise.exampleSolution || (!this.isExamMode && !this.textExercise.exampleSolutionPublicationDate),
                },
                {
                    title: 'artemisApp.exercise.sections.grading',
                    valid: Boolean(
                        this.points.valid &&
                            this.bonusPoints.valid &&
                            (this.isExamMode ||
                                (this.exerciseUpdatePlagiarismComponent?.formValid &&
                                    !this.textExercise.startDateError &&
                                    !this.textExercise.dueDateError &&
                                    !this.textExercise.assessmentDueDateError &&
                                    this.releaseDateField?.dateInput.valid &&
                                    this.startDateField?.dateInput.valid &&
                                    this.dueDateField?.dateInput.valid &&
                                    this.assessmentDateField?.dateInput.valid)),
                    ),
                    empty:
                        !this.isExamMode &&
                        // if a dayjs object contains an empty date, it is considered "invalid"
                        (!this.textExercise.startDate?.isValid() ||
                            !this.textExercise.dueDate?.isValid() ||
                            !this.textExercise.assessmentDueDate?.isValid() ||
                            !this.textExercise.releaseDate?.isValid()),
                },
            ];
        }
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.textExercise);
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.textExercise);
        this.calculateFormSectionStatus();
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.textExercise.categories = categories;
        this.exerciseCategories = categories;
    }

    save() {
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.textExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.textExercise, this.isExamMode, this.notificationText)
            .subscribe({
                next: (exercise: TextExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving = false;
                },
            });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }

    private onSaveSuccess(exercise: TextExercise) {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving = false;

        if (this.goBackAfterSaving) {
            this.navigationUtilService.navigateBack();

            return;
        }

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(errorRes: HttpErrorResponse) {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving = false;
    }
}
