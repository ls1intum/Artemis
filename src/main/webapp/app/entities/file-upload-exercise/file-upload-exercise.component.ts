import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { CourseExerciseService, CourseService } from '../course';
import { ActivatedRoute } from '@angular/router';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';
@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html'
})
export class FileUploadExerciseComponent extends ExerciseComponent {
    @Input() fileUploadExercises: FileUploadExercise[];

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseExerciseService: CourseExerciseService,
        courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        eventManager: JhiEventManager,
        route: ActivatedRoute
    ) {
        super(courseService, route, eventManager);
        this.fileUploadExercises = [];
    }

    protected loadExercises(): void {
        this.courseExerciseService.findAllFileUploadExercisesForCourse(this.courseId).subscribe(
            (res: HttpResponse<FileUploadExercise[]>) => {
                this.fileUploadExercises = res.body;
                // reconnect exercise with course
                this.fileUploadExercises.forEach(fileUploadExercise => {
                    fileUploadExercise.course = this.course;
                });
                this.emitExerciseCount(this.fileUploadExercises.length);
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    trackId(index: number, item: FileUploadExercise) {
        return item.id;
    }

    protected getChangeEventName(): string {
        return 'fileUploadExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
