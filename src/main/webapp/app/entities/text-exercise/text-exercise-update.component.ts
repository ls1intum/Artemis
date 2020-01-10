import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { Course } from '../course';
import { CourseService } from 'app/entities/course/course.service';

import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { KatexCommand } from 'app/markdown-editor/commands';
import { EditorMode } from 'app/markdown-editor';
import { MAX_SCORE_PATTERN } from 'app/app.constants';
import { AssessmentType } from 'app/entities/assessment-type';
import { WindowRef } from 'app/core/websocket/window.service';

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html',
    styleUrls: ['./text-exercise-update.scss'],
})
export class TextExerciseUpdateComponent implements OnInit {
    EditorMode = EditorMode;
    AssessmentType = AssessmentType;

    textExercise: TextExercise;
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
        private textExerciseService: TextExerciseService,
        private exerciseService: ExerciseService,
        private courseService: CourseService,
        private eventManager: JhiEventManager,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private $window: WindowRef,
    ) {}

    /**
     * Initializes all relevant data for creating or editing text exercise
     */
    ngOnInit() {
        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.
        this.$window.nativeWindow.scroll(0, 0);

        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;
            this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.textExercise);
            this.courseService.findAllCategoriesOfCourse(this.textExercise.course!.id).subscribe(
                (categoryRes: HttpResponse<string[]>) => {
                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                },
                (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
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
     * Returns to previous state, which is always exercise page
     */
    previousState() {
        if (this.textExercise.course) {
            this.router.navigate(['/course', this.textExercise.course.id]);
        } else {
            window.history.back();
        }
    }
    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.textExercise);
    }
    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.textExercise.categories = categories.map(el => JSON.stringify(el));
    }

    /**
     * Sends a request to either update or create a text exercise
     */
    save() {
        this.isSaving = true;
        if (this.textExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(this.textExerciseService.update(this.textExercise, requestOptions));
        } else {
            this.subscribeToSaveResponse(this.textExerciseService.create(this.textExercise));
        }
    }

    /**
     * Deletes example submission
     * @param id of the submission that will be deleted
     * @param index in the example submissions array
     */
    deleteExampleSubmission(id: number, index: number) {
        this.exampleSubmissionService.delete(id).subscribe(
            () => {
                this.textExercise.exampleSubmissions.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<TextExercise>>) {
        result.subscribe(
            (res: HttpResponse<TextExercise>) => this.onSaveSuccess(res.body!),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    private onSaveSuccess(result: TextExercise) {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, undefined);
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
