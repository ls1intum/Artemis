import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise, ExerciseMode, IncludedInOverallScore } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { navigateBackFromExerciseUpdate, navigateToExampleSubmissions } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { EditType } from 'app/exercises/shared/exercise/exercise-utils';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
    styleUrls: ['./modeling-exercise-update.scss'],
})
export class ModelingExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    EditorMode = EditorMode;
    AssessmentType = AssessmentType;
    UMLDiagramType = UMLDiagramType;
    checkedFlag: boolean;

    modelingExercise: ModelingExercise;
    backupExercise: ModelingExercise;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];
    examCourseId?: number;
    isImport: boolean;
    isExamMode: boolean;
    semiAutomaticAssessmentAvailable = true;

    constructor(
        private jhiAlertService: JhiAlertService,
        private modelingExerciseService: ModelingExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private exerciseGroupService: ExerciseGroupService,
        private eventManager: JhiEventManager,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
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
        this.checkedFlag = false; // default value of grading instructions toggle
        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.

        window.scroll(0, 0);

        // Get the modelingExercise
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;
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
                    if (!this.isExamMode) {
                        this.exerciseCategories = this.modelingExercise.categories || [];
                        if (!!this.modelingExercise.course) {
                            this.courseService.findAllCategoriesOfCourse(this.modelingExercise.course!.id!).subscribe(
                                (categoryRes: HttpResponse<string[]>) => {
                                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                                },
                                (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                            );
                        } else {
                            this.courseService.findAllCategoriesOfCourse(this.modelingExercise.exerciseGroup!.exam!.course!.id!).subscribe(
                                (categoryRes: HttpResponse<string[]>) => {
                                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                                },
                                (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                            );
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.modelingExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.modelingExercise.teamAssignmentConfig = undefined;
                        this.modelingExercise.teamMode = false;
                        this.modelingExercise.assessmentType = AssessmentType.MANUAL;
                    }
                    if (this.isImport) {
                        if (this.isExamMode) {
                            // The target exerciseGroupId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const courseId = params['courseId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.modelingExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.modelingExercise.course = undefined;
                        } else {
                            // The target course where we want to import into
                            const targetCourseId = params['courseId'];
                            this.courseService.find(targetCourseId).subscribe((res) => (this.modelingExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.modelingExercise.exerciseGroup = undefined;
                        }
                        // Reset the due dates
                        this.modelingExercise.dueDate = undefined;
                        this.modelingExercise.releaseDate = undefined;
                        this.modelingExercise.assessmentDueDate = undefined;
                    }
                }),
            )
            .subscribe();

        this.isSaving = false;
        this.notificationText = undefined;
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]): void {
        this.modelingExercise.categories = categories;
    }

    /**
     * Validates if the date is correct
     */
    validateDate(): void {
        this.exerciseService.validateDate(this.modelingExercise);
    }

    save() {
        if (this.modelingExercise.gradingInstructionFeedbackUsed) {
            const ref = this.popupService.checkExerciseBeforeUpdate(this.modelingExercise, this.backupExercise);
            if (!this.modalService.hasOpenModals()) {
                this.saveExercise();
            } else {
                ref.then((reference) => {
                    reference.componentInstance.confirmed.subscribe(() => {
                        this.saveExercise();
                    });
                });
            }
            return;
        }

        this.saveExercise();
    }

    /**
     * Sends a request to either update, create or import a modeling exercise
     */
    saveExercise(): void {
        Exercise.sanitize(this.modelingExercise);

        this.isSaving = true;

        switch (this.editType) {
            case EditType.IMPORT:
                this.subscribeToSaveResponse(this.modelingExerciseService.import(this.modelingExercise));
                break;
            case EditType.CREATE:
                this.subscribeToSaveResponse(this.modelingExerciseService.create(this.modelingExercise));
                break;
            case EditType.UPDATE:
                const requestOptions = {} as any;
                if (this.notificationText) {
                    requestOptions.notificationText = this.notificationText;
                }
                this.subscribeToSaveResponse(this.modelingExerciseService.update(this.modelingExercise, requestOptions));
                break;
        }
    }

    /**
     * Deletes the example submission
     * @param id of the submission that will be deleted
     * @param index in the example submissions array
     */
    deleteExampleSubmission(id: number, index: number): void {
        this.exampleSubmissionService.delete(id).subscribe(
            () => {
                this.modelingExercise.exampleSubmissions!.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }

    previousState() {
        navigateBackFromExerciseUpdate(this.router, this.modelingExercise);
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ModelingExercise>>): void {
        result.subscribe(
            (exercise: HttpResponse<ModelingExercise>) => this.onSaveSuccess(exercise.body!.id!),
            () => this.onSaveError(),
        );
    }

    private onSaveSuccess(exerciseId: number): void {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;

        switch (this.editType) {
            case EditType.CREATE:
            case EditType.IMPORT:
                // Passing exerciseId since it is required for navigation to the example submission dashboard.
                navigateToExampleSubmissions(this.router, { ...this.modelingExercise, id: exerciseId });
                break;
            case EditType.UPDATE:
                this.previousState();
                break;
        }
    }

    private onSaveError(): void {
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse): void {
        this.jhiAlertService.error(error.message);
    }

    /**
     * gets the flag of the structured grading instructions slide toggle
     */
    getCheckedFlag(event: boolean) {
        this.checkedFlag = event;
    }

    /**
     * When the diagram type changes, we need to check whether {@link AssessmentType.SEMI_AUTOMATIC} is available for the type. If not, we revert to {@link AssessmentType.MANUAL}
     */
    diagramTypeChanged() {
        const semiAutomaticSupportPossible =
            this.modelingExercise.diagramType === UMLDiagramType.ClassDiagram || this.modelingExercise.diagramType === UMLDiagramType.ActivityDiagram;
        if (this.isExamMode || !semiAutomaticSupportPossible) {
            this.modelingExercise.assessmentType = AssessmentType.MANUAL;
        }
    }
}
