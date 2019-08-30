/* angular */
import { Component, Input } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

/* 3rd party */
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';

/* application */
import { FileUploadExercise } from './file-upload-exercise.model';
import { FileUploadExercisePopupService } from './file-upload-exercise-popup.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExerciseDeleteDialogComponent } from './file-upload-exercise-delete-dialog.component';
import { CourseExerciseService, CourseService } from '../course';
import { ExerciseComponent } from 'app/entities/exercise/exercise.component';
import { AccountService } from 'app/core';

@Component({
    selector: 'jhi-file-upload-exercise',
    templateUrl: './file-upload-exercise.component.html',
})
export class FileUploadExerciseComponent extends ExerciseComponent {
    @Input() fileUploadExercises: FileUploadExercise[] = [];

    constructor(
        private fileUploadExerciseService: FileUploadExerciseService,
        private courseExerciseService: CourseExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private fileUploadExercisePopupService: FileUploadExercisePopupService,
        courseService: CourseService,
        translateService: TranslateService,
        eventManager: JhiEventManager,
        route: ActivatedRoute,
    ) {
        super(courseService, translateService, route, eventManager);
    }

    protected loadExercises(): void {
        this.courseExerciseService
            .findAllFileUploadExercisesForCourse(this.courseId)
            .pipe(filter(res => !!res.body))
            .subscribe(
                (res: HttpResponse<FileUploadExercise[]>) => {
                    this.fileUploadExercises = res.body!;
                    // reconnect exercise with course
                    this.fileUploadExercises.forEach(exercise => {
                        exercise.course = this.course;
                        exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(exercise.course);
                        exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(exercise.course);
                    });
                    this.emitExerciseCount(this.fileUploadExercises.length);
                },
                (res: HttpErrorResponse) => this.onError(res),
            );
    }

    trackId(index: number, item: FileUploadExercise) {
        return item.id;
    }

    openDeleteFileUploadExercisePopup(exerciseId: string) {
        this.fileUploadExercisePopupService.open(FileUploadExerciseDeleteDialogComponent as Component, exerciseId);
    }

    protected getChangeEventName(): string {
        return 'fileUploadExerciseListModification';
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
