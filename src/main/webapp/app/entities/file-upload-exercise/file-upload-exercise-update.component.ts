/* angular */
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

/* 3rd party */
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

/* application */
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { Course, CourseService } from 'app/entities/course';
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
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ fileUploadExercise }) => {
            this.fileUploadExercise = fileUploadExercise;
        });
        this.activatedRoute.params.subscribe(params => {
            if (params['courseId']) {
                const courseId = params['courseId'];
                this.courseService.find(courseId).subscribe(res => {
                    const course = res.body!;
                    this.fileUploadExercise.course = course;
                    this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.fileUploadExercise);
                    this.courseService.findAllCategoriesOfCourse(this.fileUploadExercise.course.id).subscribe(
                        (categoryRes: HttpResponse<string[]>) => {
                            this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body!);
                        },
                        (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                    );
                });
            }
        });
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.fileUploadExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.update(this.fileUploadExercise, this.fileUploadExercise.id));
        } else {
            this.subscribeToSaveResponse(this.fileUploadExerciseService.create(this.fileUploadExercise));
        }
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.fileUploadExercise.categories = categories.map(el => JSON.stringify(el));
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<FileUploadExercise>>) {
        result.subscribe((res: HttpResponse<FileUploadExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

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
