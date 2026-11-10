import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ExerciseDetailActionsComponent } from 'app/exercise/detail/exercise-detail-actions.component';
import { Component, DestroyRef, OnInit, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ActionType, EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { exerciseDeletionTarget, isDeletionCompleted, isDeletionOutcomeUnknown, trackAlive } from 'app/shared-ui/delete-dialog/entity-deletion-target.util';
import { EntityDeletionProgress } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
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
    private entityDeletionService = inject(EntityDeletionService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private destroyRef = inject(DestroyRef);
    private readonly isAlive = trackAlive(this.destroyRef);

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

    /** Ends a pending check for a running deletion: when the component is destroyed and when it is reused for another quiz. */
    private readonly resumeCheckEnded$ = new Subject<void>();
    private resumeCheckedQuizId?: number;

    constructor() {
        // The detail page reuses this component for another quiz. The status response of the previous quiz must not open its progress dialog over the
        // quiz that is shown now, and the quiz that is shown now is checked for a running deletion itself.
        effect(() => {
            const quizExercise = this.quizExercise();
            untracked(() => {
                if (this.resumeCheckedQuizId !== undefined && this.resumeCheckedQuizId !== quizExercise.id) {
                    this.resumeCheckEnded$.next();
                    if (this.ownsDeletion() && quizExercise.isAtLeastInstructor) {
                        this.resumeRunningDeletion();
                    }
                }
            });
        });
        this.destroyRef.onDestroy(() => this.resumeCheckEnded$.next());
    }

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

        if (this.ownsDeletion() && this.quizExercise().isAtLeastInstructor) {
            this.resumeRunningDeletion();
        }
    }

    /**
     * The detail page shows this component twice. Only the administration section, which holds the delete button, follows a deletion,
     * so that a running deletion is neither checked nor handled twice.
     */
    private ownsDeletion(): boolean {
        return this.isDetailPage() && this.detailPageSection() === 'administration';
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
        const quizExercise = this.quizExercise();
        this.entityDeletionService.delete(exerciseDeletionTarget(quizExercise), this.quizExerciseDeletionApi.deleteQuizExercise(quizExercise.id!)).subscribe({
            next: (progress) => {
                this.onDeletionEnded(progress, quizExercise.id);
                // A failed deletion is reported by the progress dialog; this only ends the confirmation dialog.
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Shows the progress of a deletion of this quiz that is already running, e.g. after the page was reloaded or another instructor started it.
     */
    private resumeRunningDeletion() {
        const quizExercise = this.quizExercise();
        this.resumeCheckedQuizId = quizExercise.id;
        this.entityDeletionService.resume(exerciseDeletionTarget(quizExercise), this.resumeCheckEnded$).subscribe((progress) => this.onDeletionEnded(progress, quizExercise.id));
    }

    private onDeletionEnded(progress: EntityDeletionProgress, deletedQuizId: number | undefined) {
        if (isDeletionCompleted(progress)) {
            this.eventManager.broadcast({
                name: 'quizExerciseListModification',
                content: 'Deleted an quizExercise',
            });
            // The detail page reuses this component for other quizzes, so it must still show the deleted quiz before it navigates away from it.
            if (this.isDetailPage() && this.isAlive() && this.quizExercise().id === deletedQuizId) {
                void this.router.navigate(['course-management', this.courseId, 'exercises']);
            }
        } else if (isDeletionOutcomeUnknown(progress)) {
            // Not known how it ended: only refresh the list, which shows the real state, instead of announcing a deletion or leaving the page.
            this.eventManager.broadcast({ name: 'quizExerciseListModification', content: 'Refresh after an unknown deletion outcome' });
            // The detail page reloads the quiz, which tells whether it still exists.
            this.loadQuizExercises.emit();
        }
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

    fetchExerciseDeletionSummary(): Observable<EntitySummaryCategory[]> {
        return this.exerciseService.getDeletionSummary(this.quizExercise());
    }
}
