import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

@Component({
    selector: 'jhi-exercise-row-buttons',
    templateUrl: './exercise-row-buttons.component.html',
})
export class ExerciseRowButtonsComponent {
    @Input() courseId: number;
    @Input() exercise: Exercise;
    @Input() examMode = false;
    @Input() examId: number;
    @Input() exerciseGroupId: number;
    @Output() onDeleteExercise = new EventEmitter<void>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    exerciseType = ExerciseType;

    constructor(
        private textExerciseService: TextExerciseService,
        private fileUploadExerciseService: FileUploadExerciseService,
        private programmingExerciseService: ProgrammingExerciseService,
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
            case ExerciseType.QUIZ:
                this.deleteQuizExercise();
                break;
        }
    }

    private deleteTextExercise() {
        this.textExerciseService.delete(this.exercise.id).subscribe(
            () => {
                // TODO: Should we choose another event name for exam exercises?
                this.eventManager.broadcast({
                    name: 'textExerciseListModification',
                    content: 'Deleted a textExercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
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
                this.onDeleteExercise.emit();
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    private deleteQuizExercise() {
        this.quizExerciseService.delete(this.exercise.id).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Deleted a quiz',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
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

    /**
     * Assemble the router link for editing the exercise.
     */
    createRouterLink(type: string): any[] {
        let link;
        if (this.examMode) {
            link = ['/course-management', this.courseId, 'exams', this.examId, 'exercise-groups', this.exerciseGroupId, `${this.exercise.type}-exercises`];
        } else {
            link = ['/course-management', this.courseId, `${this.exercise.type}-exercises`];
        }

        switch (type) {
            case 'edit':
                link = link.concat([this.exercise.id, 'edit']);
                break;
        }
        return link;
    }
}
