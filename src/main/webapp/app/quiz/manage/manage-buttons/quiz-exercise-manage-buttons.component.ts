import { Component, OnInit, inject, input, output } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faClipboardCheck, faEye, faFileExport, faListAlt, faSignal, faTable, faTrash, faUndo, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

@Component({
    selector: 'jhi-quiz-exercise-manage-buttons',
    templateUrl: './quiz-exercise-manage-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, ButtonComponent, DeleteButtonDirective],
})
export class QuizExerciseManageButtonsComponent implements OnInit {
    private quizExerciseService = inject(QuizExerciseService);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private exerciseService = inject(ExerciseService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    protected readonly ActionType = ActionType;
    readonly faEye = faEye;
    readonly faSignal = faSignal;
    readonly faTable = faTable;
    readonly faFileExport = faFileExport;
    readonly faWrench = faWrench;
    readonly faTrash = faTrash;
    readonly faListAlt = faListAlt;
    readonly faUndo = faUndo;
    readonly faClipboardCheck = faClipboardCheck;

    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    courseId: number;
    examId: number;
    isExamMode: boolean;

    baseUrl: string;
    isEvaluatingQuizExercise: boolean;

    isDetailPage = input(false);

    quizExercise = input.required<QuizExercise>();

    loadQuizExercises = output<void>();

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        const groupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        if (this.examId && groupId) {
            this.isExamMode = true;
        }

        if (this.isExamMode) {
            this.baseUrl = `/course-management/${this.courseId}/exams/${this.examId}/exercise-groups/${groupId}`;
        } else {
            this.baseUrl = `/course-management/${this.courseId}`;
        }
    }

    /**
     * Exports questions for the given quiz exercise in json file
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuizExercise(exportAll: boolean) {
        this.quizExerciseService.find(this.quizExercise().id!).subscribe((response: HttpResponse<QuizExercise>) => {
            const exercise = response.body!;
            this.quizExerciseService.exportQuiz(exercise.quizQuestions, exportAll, exercise.title);
        });
    }

    /**
     * Deletes quiz exercise
     */
    deleteQuizExercise() {
        this.quizExerciseService.delete(this.quizExercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Deleted an quizExercise',
                });
                this.dialogErrorSource.next('');
                if (this.isDetailPage()) {
                    this.router.navigate(['course-management', this.quizExercise().course!.id, 'exercises']);
                }
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Resets quiz exercise
     */
    resetQuizExercise() {
        this.exerciseService.reset(this.quizExercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Reset an quizExercise',
                });
                this.dialogErrorSource.next('');
                this.alertService.success('artemisApp.quizExercise.resetSuccessful', { title: this.quizExercise().title });
                this.loadQuizExercises.emit();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    evaluateQuizExercise() {
        this.isEvaluatingQuizExercise = true;
        this.exerciseService.evaluateQuizExercise(this.quizExercise().id!).subscribe({
            next: () => {
                this.alertService.success('artemisApp.quizExercise.evaluateQuizExerciseSuccess');
                this.isEvaluatingQuizExercise = false;
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
                this.isEvaluatingQuizExercise = false;
            },
        });
    }
}
