import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faBook, faExclamationTriangle, faEye, faFileExport, faFileSignature, faPencilAlt, faSignal, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

@Component({
    selector: 'jhi-exam-exercise-row-buttons',
    templateUrl: './exam-exercise-row-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, NgbTooltip, DeleteButtonDirective, ArtemisTranslatePipe],
})
export class ExamExerciseRowButtonsComponent implements OnInit {
    private textExerciseService = inject(TextExerciseService);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private modelingExerciseService = inject(ModelingExerciseService);
    private quizExerciseService = inject(QuizExerciseService);
    private exerciseService = inject(ExerciseService);
    private eventManager = inject(EventManager);
    private profileService = inject(ProfileService);

    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() exam: Exam;
    @Input() exerciseGroupId: number;
    @Input() latestIndividualEndDate: dayjs.Dayjs | undefined;
    @Output() onDeleteExercise = new EventEmitter<void>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    exerciseType = ExerciseType;

    // Icons
    faTrash = faTrash;
    faBook = faBook;
    faEye = faEye;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    faExclamationTriangle = faExclamationTriangle;
    faSignal = faSignal;
    faPencilAlt = faPencilAlt;
    faFileExport = faFileExport;
    faFileSignature = faFileSignature;
    farListAlt = faListAlt;

    localCIEnabled = true;

    ngOnInit(): void {
        this.localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    }

    /**
     * Checks whether the exam is over using the latestIndividualEndDate
     */
    isExamOver() {
        return this.latestIndividualEndDate ? this.latestIndividualEndDate.isBefore(dayjs()) : false;
    }

    /**
     * Checks whether the exam has started
     */
    hasExamStarted() {
        return this.exam.startDate ? this.exam.startDate.isBefore(dayjs()) : false;
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
            case ExerciseType.QUIZ:
                this.deleteQuizExercise();
                break;
            case ExerciseType.MODELING:
                this.deleteModelingExercise();
                break;
        }
    }

    private deleteTextExercise() {
        this.textExerciseService.delete(this.exercise.id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'textExerciseListModification',
                    content: 'Deleted a textExercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private deleteModelingExercise() {
        this.modelingExerciseService.delete(this.exercise.id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'modelingExerciseListModification',
                    content: 'Deleted a modelingExercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private deleteFileUploadExercise() {
        this.fileUploadExerciseService.delete(this.exercise.id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'fileUploadExerciseListModification',
                    content: 'Deleted a fileUploadExercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private deleteQuizExercise() {
        this.quizExerciseService.delete(this.exercise.id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Deleted a quiz',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    public deleteProgrammingExercise(event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.exercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted a programming exercise',
                });
                this.dialogErrorSource.next('');
                this.onDeleteExercise.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    fetchExerciseDeletionSummary(): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(this.exercise);
    }

    /**
     * Exports questions for the given quiz exercise in json file
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuizById(exportAll: boolean) {
        this.quizExerciseService.find(this.exercise.id!).subscribe((res: HttpResponse<QuizExercise>) => {
            const exercise = res.body!;
            this.quizExerciseService.exportQuiz(exercise.quizQuestions, exportAll, exercise.title);
        });
    }

    protected readonly RepositoryType = RepositoryType;
}
