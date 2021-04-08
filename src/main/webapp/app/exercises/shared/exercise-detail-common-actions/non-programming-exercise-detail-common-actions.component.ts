import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Subject } from 'rxjs';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { JhiEventManager } from 'ng-jhipster';
import { HttpErrorResponse } from '@angular/common/http';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';

@Component({
    selector: 'jhi-non-programming-exercise-detail-common-actions',
    templateUrl: './non-programming-exercise-detail-common-actions.component.html',
    styles: [],
})
export class NonProgrammingExerciseDetailCommonActionsComponent {
    @Input()
    exercise: Exercise;

    @Input()
    courseId: number;

    @Input()
    isExamExercise = false;

    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private textExerciseService: TextExerciseService,
        private fileUploadExerciseService: FileUploadExerciseService,
        private modelingExerciseService: ModelingExerciseService,
        private eventManager: JhiEventManager,
    ) {}

    /**
     * Returns the route for editing the exercise. Exam and course exercises have different routes.
     */
    getEditRoute() {
        if (this.isExamExercise) {
            return [
                '/course-management',
                this.exercise.exerciseGroup?.exam?.course?.id,
                'exams',
                this.exercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                this.exercise.exerciseGroup?.id,
                this.exercise.type! + '-exercises',
                this.exercise.id,
                'edit',
            ];
        } else {
            return ['/course-management', this.courseId, this.exercise.type! + '-exercises', this.exercise.id, 'edit'];
        }
    }

    deleteExercise() {
        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                this.textExerciseService.delete(this.exercise.id!).subscribe(
                    () => {
                        this.eventManager.broadcast({
                            name: 'textExerciseListModification',
                            content: 'Deleted a textExercise',
                        });
                        this.dialogErrorSource.next('');
                    },
                    (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
                );
                break;
            case ExerciseType.FILE_UPLOAD:
                this.fileUploadExerciseService.delete(this.exercise.id!).subscribe(
                    () => {
                        this.eventManager.broadcast({
                            name: 'fileUploadExerciseListModification',
                            content: 'Deleted an fileUploadExercise',
                        });
                        this.dialogErrorSource.next('');
                    },
                    (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
                );
                break;
            case ExerciseType.MODELING:
                this.modelingExerciseService.delete(this.exercise.id!).subscribe(
                    () => {
                        this.eventManager.broadcast({
                            name: 'modelingExerciseListModification',
                            content: 'Deleted an modelingExercise',
                        });
                        this.dialogErrorSource.next('');
                    },
                    (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
                );
                break;
            default:
        }
    }
}
