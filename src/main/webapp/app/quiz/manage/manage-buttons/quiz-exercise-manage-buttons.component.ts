import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ExerciseDetailActionsComponent } from 'app/exercise/detail/exercise-detail-actions.component';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ActionType, EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faClipboardCheck, faEllipsis, faEye, faFileExport, faListAlt, faSignal, faTrash, faUndo, faWrench } from '@fortawesome/free-solid-svg-icons';
import { Observable, Subject } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';

import { CreateVariantWithAiButtonComponent } from 'app/hyperion/variants/create-variant-with-ai-button.component';
import { QuizExerciseDeletionApi } from 'app/openapi/api/quiz-exercise-deletion-api';
import { QuizExerciseEvaluationApi } from 'app/openapi/api/quiz-exercise-evaluation-api';

@Component({
    selector: 'jhi-quiz-exercise-manage-buttons',
    templateUrl: './quiz-exercise-manage-buttons.component.html',
    imports: [
        RouterLink,
        NgTemplateOutlet,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        DeleteButtonDirective,
        CreateVariantWithAiButtonComponent,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiTooltipDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        ExerciseDetailActionsComponent,
    ],
})
export class QuizExerciseManageButtonsComponent implements OnInit {
    private quizExerciseService = inject(QuizExerciseService);
    private quizExerciseDeletionApi = inject(QuizExerciseDeletionApi);
    private quizExerciseEvaluationApi = inject(QuizExerciseEvaluationApi);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private exerciseService = inject(ExerciseService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

    protected readonly ActionType = ActionType;
    readonly faEllipsis = faEllipsis;
    readonly faEye = faEye;
    readonly faSignal = faSignal;
    readonly faFileExport = faFileExport;
    readonly faWrench = faWrench;
    readonly faTrash = faTrash;
    readonly faListAlt = faListAlt;
    readonly faUndo = faUndo;
    readonly faClipboardCheck = faClipboardCheck;

    protected dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    courseId!: number; // set in ngOnInit() from route params
    examId!: number; // set in ngOnInit() from route params
    isExamMode = false;

    readonly baseUrl = signal<string>(undefined!);
    readonly isEvaluatingQuizExercise = signal(false);
    readonly evaluationError = signal<string | undefined>(undefined);

    isDetailPage = input(false);
    readonly detailPageSection = input<'actions' | 'administration'>('actions');

    quizExercise = input.required<QuizExercise>();
    course = computed(() => getCourseFromExercise(this.quizExercise()));

    loadQuizExercises = output<void>();

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        const groupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        if (this.examId && groupId) {
            this.isExamMode = true;
        }

        if (this.isExamMode) {
            this.baseUrl.set(`/course-management/${this.courseId}/exams/${this.examId}/exercise-groups/${groupId}`);
        } else {
            this.baseUrl.set(`/course-management/${this.courseId}`);
        }
    }

    /**
     * Exports questions for the given quiz exercise in json file
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuizExercise(exportAll: boolean) {
        (this.isExamMode || this.quizExercise().exerciseGroup
            ? this.quizExerciseService.findForExam(this.quizExercise().id!)
            : this.quizExerciseService.find(this.quizExercise().id!)
        ).subscribe((exercise) => {
            this.quizExerciseService.exportQuiz(exercise.quizQuestions, exportAll, exercise.title);
        });
    }

    /**
     * Deletes quiz exercise
     */
    deleteQuizExercise() {
        this.quizExerciseDeletionApi.deleteQuizExercise(this.quizExercise().id!).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'quizExerciseListModification',
                    content: 'Deleted an quizExercise',
                });
                this.dialogErrorSource.next('');
                if (this.isDetailPage()) {
                    void this.router.navigate(['course-management', this.courseId, 'exercises']);
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
        this.evaluationError.set(undefined);
        this.isEvaluatingQuizExercise.set(true);
        this.quizExerciseEvaluationApi.evaluateQuizExercise(this.quizExercise().id!).subscribe({
            next: () => {
                this.alertService.success('artemisApp.quizExercise.evaluateQuizExerciseSuccess');
                this.isEvaluatingQuizExercise.set(false);
            },
            error: (error: HttpErrorResponse) => {
                // The global alert handler ignores 404 responses and most errors without a problem title.
                // Connection, permission and structured validation failures already receive a global alert.
                const hasValidationAlert =
                    error.status === 400 && (error.error?.fieldErrors?.length || error.headers.keys().some((key) => key.toLowerCase().endsWith('app-error')));
                if (error.status === 404 || (error.status !== 0 && error.status !== 403 && !error.error?.title && !hasValidationAlert)) {
                    this.evaluationError.set(error.message);
                }
                this.isEvaluatingQuizExercise.set(false);
            },
        });
    }

    fetchExerciseDeletionSummary(): Observable<EntitySummary> {
        return this.exerciseService.getDeletionSummary(this.quizExercise());
    }
}
