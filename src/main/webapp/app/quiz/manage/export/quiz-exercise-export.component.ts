import { ChangeDetectionStrategy, Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';

import { QuizExerciseService } from '../service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { ButtonModule } from 'primeng/button';
import { TumUiDialogComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-quiz-exercise-export',
    templateUrl: './quiz-exercise-export.component.html',
    styleUrls: ['./quiz-exercise-export.component.scss', '../../shared/quiz.scss'],
    imports: [TranslateDirective, ArtemisTranslatePipe, FormsModule, FaIconComponent, ButtonModule, TumUiDialogComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizExerciseExportComponent {
    private quizExerciseService = inject(QuizExerciseService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);

    /** Two-way visibility, driven by the parent. */
    readonly visible = model<boolean>(false);
    /** Course whose quizzes are offered for export, supplied by the parent. */
    readonly courseId = input.required<number>();
    /** Emitted when the user presses "Back", so the caller can reopen the manage-exercises modal. */
    readonly back = output<void>();

    readonly questions = signal<QuizQuestion[]>([]);
    readonly course = signal<Course | undefined>(undefined);
    readonly isLoading = signal(false);

    protected readonly faArrowLeft = faArrowLeft;

    constructor() {
        // Load on each open; untracked so a mid-open change doesn't re-trigger.
        effect(() => {
            if (this.visible()) {
                untracked(() => this.loadForCourse(this.courseId()));
            }
        });
    }

    /**
     * Loads the course and its quiz questions. The questions are collected via forkJoin and assigned in one step,
     * so the list renders at full length instead of growing (and resizing the dialog) as each quiz loads.
     * @param courseId Id of the course
     */
    private loadForCourse(courseId: number) {
        this.isLoading.set(true);
        this.courseService.find(courseId).subscribe({
            error: (error: HttpErrorResponse) => {
                this.isLoading.set(false);
                onError(this.alertService, error);
            },
            next: (courseResponse) => {
                this.course.set(courseResponse.body!);
                // List the course's quizzes, then load each quiz's questions in parallel.
                this.quizExerciseService.findForCourse(courseId).subscribe({
                    next: (res: HttpResponse<QuizExercise[]>) => {
                        const quizExercises = (res.body ?? []).filter((quizExercise): quizExercise is QuizExercise & { id: number } => quizExercise.id !== undefined);
                        if (quizExercises.length === 0) {
                            this.questions.set([]);
                            this.isLoading.set(false);
                            return;
                        }
                        forkJoin(quizExercises.map((quizExercise) => this.quizExerciseService.find(quizExercise.id))).subscribe({
                            next: (responses: HttpResponse<QuizExercise>[]) => {
                                const collected: QuizQuestion[] = [];
                                responses.forEach((response, index) => {
                                    const quizExercise = quizExercises[index];
                                    // reconnect course and exercise in case we need this information later
                                    quizExercise.course = this.course();
                                    response.body?.quizQuestions?.forEach((question) => {
                                        question.exercise = quizExercise;
                                        collected.push(question);
                                    });
                                });
                                this.questions.set(collected);
                                this.isLoading.set(false);
                            },
                            error: (error: HttpErrorResponse) => {
                                this.isLoading.set(false);
                                onError(this.alertService, error);
                            },
                        });
                    },
                    error: (error: HttpErrorResponse) => {
                        this.isLoading.set(false);
                        onError(this.alertService, error);
                    },
                });
            },
        });
    }

    /** Exports selected questions into a json file. */
    exportQuiz() {
        this.quizExerciseService.exportQuiz(this.questions(), false);
        this.visible.set(false);
    }

    /** Emits {@link back} and closes, so the caller can reopen the manage-exercises modal. */
    onBack() {
        this.back.emit();
        this.visible.set(false);
    }
}
