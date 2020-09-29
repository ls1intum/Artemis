import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Subject } from 'rxjs';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { JhiEventManager } from 'ng-jhipster';
import { HttpErrorResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-exercise-row-button',
    templateUrl: './exercise-row-button.component.html',
    styles: [],
})
export class ExerciseRowButtonComponent {
    readonly ActionType = ActionType;
    exerciseType = ExerciseType;
    FeatureToggle = FeatureToggle;

    @Input() exercise: Exercise;
    @Input() course: Course;
    @Output() onDeleteExercise = new EventEmitter<{ exerciseId: number; groupId: number }>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    constructor(
        private exerciseService: ExerciseService,
        private textExerciseService: TextExerciseService,
        private fileUploadExerciseService: FileUploadExerciseService,
        private programmingExerciseService: ProgrammingExerciseService,
        private modelingExerciseService: ModelingExerciseService,
        private quizExerciseService: QuizExerciseService,
        private eventManager: JhiEventManager,
    ) {}

    /**
     * Deletes an exercise. ExerciseType is used to choose the right service for deletion.
     */
    deleteExercise() {
        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                this.deleteTextExercise();
                break;
            case ExerciseType.FILE_UPLOAD:
                this.deleteFileUploadExercise();
                break;
            case ExerciseType.MODELING:
                this.deleteModelingExercise();
                break;
        }
    }

    private deleteTextExercise() {
        this.textExerciseService.delete(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'textExerciseListModification',
                    content: 'Deleted a textExercise',
                });
                this.dialogErrorSource.next('');
                if (this.exercise.exerciseGroup) {
                    this.onDeleteExercise.emit({ exerciseId: this.exercise.id, groupId: this.exercise.exerciseGroup.id });
                }
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    private deleteModelingExercise() {
        this.modelingExerciseService.delete(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'modelingExerciseListModification',
                    content: 'Deleted a modelingExercise',
                });
                this.dialogErrorSource.next('');
                if (this.exercise.exerciseGroup) {
                    this.onDeleteExercise.emit({ exerciseId: this.exercise.id, groupId: this.exercise.exerciseGroup.id });
                }
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    private deleteFileUploadExercise() {
        this.fileUploadExerciseService.delete(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'fileUploadExerciseListModification',
                    content: 'Deleted a fileUploadExercise',
                });
                this.dialogErrorSource.next('');
                if (this.exercise.exerciseGroup) {
                    this.onDeleteExercise.emit({ exerciseId: this.exercise.id, groupId: this.exercise.exerciseGroup.id });
                }
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
