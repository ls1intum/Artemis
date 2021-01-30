import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextExerciseService } from './text-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exercise, ExerciseCategory, ExerciseMode, IncludedInOverallScore } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { NgForm } from '@angular/forms';
import { navigateBackFromExerciseUpdate } from 'app/utils/navigation.utils';

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html',
    styleUrls: ['./text-exercise-update.scss'],
})
export class TextExerciseUpdateComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @ViewChild('editForm') editForm: NgForm;

    examCourseId?: number;
    checkedFlag: boolean;
    isExamMode: boolean;
    isImport = false;
    EditorMode = EditorMode;
    AssessmentType = AssessmentType;

    textExercise: TextExercise;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];

    constructor(
        private jhiAlertService: JhiAlertService,
        private textExerciseService: TextExerciseService,
        private exerciseService: ExerciseService,
        private exerciseGroupService: ExerciseGroupService,
        private courseService: CourseManagementService,
        private eventManager: JhiEventManager,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
    ) {}

    /**
     * Initializes all relevant data for creating or editing text exercise
     */
    ngOnInit() {
        this.checkedFlag = false; // default value of grading instructions toggle

        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.
        window.scroll(0, 0);

        // Get the textExercise
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;
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
                        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.textExercise);
                        if (this.examCourseId) {
                            this.courseService.findAllCategoriesOfCourse(this.examCourseId).subscribe(
                                (categoryRes: HttpResponse<string[]>) => {
                                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                                },
                                (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                            );
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.textExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.textExercise.teamAssignmentConfig = undefined;
                        this.textExercise.teamMode = false;
                    }
                    if (this.isImport) {
                        if (this.isExamMode) {
                            // The target exerciseId where we want to import into
                            const exerciseGroupId = params['groupId'];
                            const courseId = params['courseId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.textExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.textExercise.course = undefined;
                        } else {
                            // The target course where we want to import into
                            const targetCourseId = params['courseId'];
                            this.courseService.find(targetCourseId).subscribe((res) => (this.textExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.textExercise.exerciseGroup = undefined;
                        }
                        // Reset the due dates
                        this.textExercise.dueDate = undefined;
                        this.textExercise.releaseDate = undefined;
                        this.textExercise.assessmentDueDate = undefined;
                    }
                }),
            )
            .subscribe();
        this.isSaving = false;
        this.notificationText = undefined;
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing exercise
     * Returns to the overview page if there is no previous state and we created a new exercise
     * Returns to the exercise group page if we are in exam mode
     */
    previousState() {
		navigateBackFromExerciseUpdate(this.router, this.textExercise);
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
        this.textExercise.categories = categories.map((el) => JSON.stringify(el));
    }

    /**
     * Sends a request to either update or create a text exercise
     */
    save() {
        Exercise.sanitize(this.textExercise);

        this.isSaving = true;
        if (this.isImport) {
            this.subscribeToSaveResponse(this.textExerciseService.import(this.textExercise));
        } else if (this.textExercise.id !== undefined) {
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
                this.textExercise.exampleSubmissions!.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<TextExercise>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    private onSaveSuccess() {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    /**
     * gets the flag of the structured grading instructions slide toggle
     */
    getCheckedFlag(event: boolean) {
        this.checkedFlag = event;
    }
}
