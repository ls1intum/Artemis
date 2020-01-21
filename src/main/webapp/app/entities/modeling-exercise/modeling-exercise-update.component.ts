import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { Course } from '../course';
import { CourseService } from 'app/entities/course/course.service';

import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { KatexCommand } from 'app/markdown-editor/commands';
import { EditorMode } from 'app/markdown-editor';
import { MAX_SCORE_PATTERN } from 'app/app.constants';
import { WindowRef } from 'app/core/websocket/window.service';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
    styleUrls: ['./modeling-exercise-update.scss'],
})
export class ModelingExerciseUpdateComponent implements OnInit {
    EditorMode = EditorMode;

    modelingExercise: ModelingExercise;
    isSaving: boolean;
    maxScorePattern = MAX_SCORE_PATTERN;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText: string | null;

    courses: Course[];

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];

    constructor(
        private jhiAlertService: JhiAlertService,
        private modelingExerciseService: ModelingExerciseService,
        private courseService: CourseService,
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
    ngOnInit() {
        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.
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
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.modelingExercise.categories = categories.map(el => JSON.stringify(el));
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.modelingExercise);
    }

    /**
     * Sends a request to either update or create a modeling exercise
     */
    save() {
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
    deleteExampleSubmission(id: number, index: number) {
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
    previousState() {
        if (this.modelingExercise.course) {
            this.router.navigate(['/course', this.modelingExercise.course.id]);
        } else {
            window.history.back();
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ModelingExercise>>) {
        result.subscribe(
            (res: HttpResponse<ModelingExercise>) => this.onSaveSuccess(res.body!),
            (res: HttpErrorResponse) => this.onSaveError(),
        );
    }

    private onSaveSuccess(result: ModelingExercise) {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a course in the collection
     * @param item current course
     */
    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}
