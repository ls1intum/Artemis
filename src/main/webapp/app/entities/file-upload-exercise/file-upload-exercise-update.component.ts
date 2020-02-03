import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { EditorMode } from 'app/markdown-editor';
import { KatexCommand } from 'app/markdown-editor/commands';
import { MAX_SCORE_PATTERN } from 'app/app.constants';

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html',
    styleUrls: ['./file-upload-exercise-update.component.scss'],
})
export class FileUploadExerciseUpdateComponent implements OnInit {
    fileUploadExercise: FileUploadExercise;
    isSaving: boolean;
    maxScorePattern = MAX_SCORE_PATTERN;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    courses: Course[];
    EditorMode = EditorMode;
    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    domainCommandsGradingInstructions = [new KatexCommand()];

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private activatedRoute: ActivatedRoute,
        private courseService: CourseService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
    ) {}

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
            this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.fileUploadExercise);
            this.courseService.findAllCategoriesOfCourse(this.fileUploadExercise.course!.id).subscribe(
                (categoryRes: HttpResponse<string[]>) => {
                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                },
                (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
            );
        });
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    /**
     * Returns to previous state, which should be always the page of selected course
     */
    previousState() {
        if (this.fileUploadExercise.course) {
            this.router.navigate(['/course', this.fileUploadExercise.course!.id]);
        } else {
            window.history.back();
        }
    }

    /**
     * Creates or updates file upload exercise
     */
    save() {
        this.isSaving = true;
        if (this.fileUploadExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.update(this.fileUploadExercise, this.fileUploadExercise.id));
        } else {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.create(this.fileUploadExercise));
        }
    }
    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.fileUploadExercise);
    }
    /**
     * Updates categories for file upload exercise
     * @param categories list of exercies categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.fileUploadExercise.categories = categories.map(el => JSON.stringify(el));
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<FileUploadExercise>>) {
        result.subscribe(
            (res: HttpResponse<FileUploadExercise>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(),
        );
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a course in the collection
     * @param item current course
     */
    trackCourseById(index: number, item: Course) {
        return item.id;
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }
}
