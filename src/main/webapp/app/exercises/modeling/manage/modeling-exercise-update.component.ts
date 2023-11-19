import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseMode, IncludedInOverallScore, resetDates } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise.utils';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from '../shared/modeling-editor.component';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { loadCourseExerciseCategories } from 'app/exercises/shared/course-exercises/course-utils';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
})
export class ModelingExerciseUpdateComponent implements OnInit {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor?: ModelingEditorComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly documentationType: DocumentationType = 'Model';

    EditorMode = EditorMode;
    AssessmentType = AssessmentType;
    UMLDiagramType = UMLDiagramType;

    modelingExercise: ModelingExercise;
    backupExercise: ModelingExercise;
    exampleSolution: UMLModel;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;
    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    examCourseId?: number;
    isImport: boolean;
    isExamMode: boolean;
    semiAutomaticAssessmentAvailable = true;
    goBackAfterSaving = false;

    // Icons
    faSave = faSave;
    faBan = faBan;

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
    ) {}

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.modelingExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
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
                        resetDates(this.modelingExercise);
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
