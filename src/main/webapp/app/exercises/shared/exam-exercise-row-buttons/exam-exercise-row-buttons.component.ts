import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam/exam.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { faBook, faExclamationTriangle, faEye, faFileExport, faFileSignature, faPencilAlt, faSignal, faTable, faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { PROFILE_LOCALCI, PROFILE_LOCALVC } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-exam-exercise-row-buttons',
    templateUrl: './exam-exercise-row-buttons.component.html',
})
export class ExamExerciseRowButtonsComponent implements OnInit {
    private textExerciseService = inject(TextExerciseService);
    private fileUploadExerciseService = inject(FileUploadExerciseService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private modelingExerciseService = inject(ModelingExerciseService);
    private quizExerciseService = inject(QuizExerciseService);
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

    localVCEnabled = false;
    localCIEnabled = false;

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
            this.localCIEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALCI);
        });
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
}
