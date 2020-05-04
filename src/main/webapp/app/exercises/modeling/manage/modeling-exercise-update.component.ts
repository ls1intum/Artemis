import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { MAX_SCORE_PATTERN } from 'app/app.constants';
import { WindowRef } from 'app/core/websocket/window.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseCategory } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { AlertService } from 'app/core/alert/alert.service';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
    styleUrls: ['./modeling-exercise-update.scss'],
})
export class ModelingExerciseUpdateComponent implements OnInit {
    EditorMode = EditorMode;
    checkedFlag: boolean;

    modelingExercise: ModelingExercise;
    isSaving: boolean;
    maxScorePattern = MAX_SCORE_PATTERN;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText: string | null;

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];

    constructor(
        private jhiAlertService: AlertService,
        private modelingExerciseService: ModelingExerciseService,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private eventManager: JhiEventManager,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private $window: WindowRef,
    ) {}

    /**
     * Initializes all relevant data for creating or editing modeling exercise
     */
    ngOnInit(): void {
        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.
        this.checkedFlag = false; // default value of grading instructions toggle
        this.$window.nativeWindow.scroll(0, 0);

        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;
            this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.modelingExercise);
            this.courseService.findAllCategoriesOfCourse(this.modelingExercise.course!.id).subscribe(
                (res: HttpResponse<string[]>) => {
                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(res.body!);
                },
                (res: HttpErrorResponse) => this.onError(res),
            );
        });
        this.isSaving = false;
        this.notificationText = null;
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]): void {
        this.modelingExercise.categories = categories.map((el) => JSON.stringify(el));
    }

    /**
     * Validates if the date is correct
     */
    validateDate(): void {
        this.exerciseService.validateDate(this.modelingExercise);
    }

    /**
     * Sends a request to either update or create a modeling exercise
     */
    save(): void {
        this.isSaving = true;
        if (this.modelingExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(this.modelingExerciseService.update(this.modelingExercise, requestOptions));
        } else {
            this.subscribeToSaveResponse(this.modelingExerciseService.create(this.modelingExercise));
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
                this.modelingExercise.exampleSubmissions.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }

    /**
     * Returns to previous state, which is always exercise page
     */
    previousState(): void {
        window.history.back();
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ModelingExercise>>): void {
        result.subscribe(
            () => this.onSaveSuccess(),
            () => this.onSaveError(),
        );
    }

    private onSaveSuccess(): void {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.previousState();
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
}
