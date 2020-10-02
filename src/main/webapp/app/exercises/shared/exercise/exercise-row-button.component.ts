import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
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
import * as moment from 'moment';

@Component({
    selector: 'jhi-exercise-row-button',
    templateUrl: './exercise-row-button.component.html',
    styles: [],
})
export class ExerciseRowButtonComponent implements OnInit {
    readonly ActionType = ActionType;
    exerciseType = ExerciseType;
    FeatureToggle = FeatureToggle;

    @Input() exercise: Exercise;
    @Input() course: Course;
    @Input() isInExerciseGroup = false;
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

    ngOnInit(): void {
        console.log(this.exercise.exerciseGroup);
    }
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

    public deleteProgrammingExercise($event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.exercise.id, $event.deleteStudentReposBuildPlans, $event.deleteBaseReposBuildPlans).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted a programming exercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    resetExercise() {
        switch (this.exercise.type) {
            case ExerciseType.QUIZ:
                this.resetQuizExercise();
                break;
            case ExerciseType.PROGRAMMING:
                this.resetProgrammingExercise();
                break;
        }
    }

    /**
     * Resets quiz exercise
     * @param quizExerciseId id of the quiz exercise that will be deleted
     */
    resetQuizExercise() {
        this.quizExerciseService.reset(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Reset an quizExercise',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.headers.get('X-artemisApp-error')!),
        );
    }

    /**
     * Resets programming exercise
     * @param programmingExerciseId the id of the programming exercise that we want to delete
     */
    resetProgrammingExercise() {
        this.exerciseService.reset(this.exercise.id).subscribe(
            () => this.dialogErrorSource.next(''),
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Checks whether the exam is over using the endDate
     */
    isExamOver() {
        return this.exercise.exerciseGroup?.exam?.endDate ? this.exercise.exerciseGroup.exam.endDate.isBefore(moment()) : false;
    }

    /**
     * Checks whether the exam has started
     */
    hasExamStarted() {
        return this.exercise.exerciseGroup?.exam?.startDate ? this.exercise.exerciseGroup.exam.startDate.isBefore(moment()) : false;
    }
}
