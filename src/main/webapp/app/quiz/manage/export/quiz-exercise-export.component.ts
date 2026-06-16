import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { forkJoin } from 'rxjs';

import { QuizExerciseService } from '../service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { ButtonModule } from 'primeng/button';

/** Sentinel the export dialog closes with when the user presses "Back", so the caller can reopen the manage-exercises modal. */
export const QUIZ_EXPORT_BACK = '__quiz_export_back__';

@Component({
    selector: 'jhi-quiz-exercise-export',
    templateUrl: './quiz-exercise-export.component.html',
    styleUrls: ['./quiz-exercise-export.component.scss', '../../shared/quiz.scss'],
    imports: [TranslateDirective, FormsModule, FaIconComponent, ButtonModule],
})
export class QuizExerciseExportComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private quizExerciseService = inject(QuizExerciseService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    // Optional injections: this component is opened both as a routed page and via PrimeNG DialogService (as a modal).
    private dialogConfig = inject(DynamicDialogConfig, { optional: true });
    private dialogRef = inject(DynamicDialogRef, { optional: true });

    questions: QuizQuestion[] = new Array(0);
    courseId: number;
    course: Course;
    isLoading = false;

    protected readonly faArrowLeft = faArrowLeft;

    /** True when this component is shown inside a PrimeNG dialog (vs. as a routed page). */
    get isDialog(): boolean {
        return this.dialogRef !== null;
    }

    /**
     * Load the quizzes of the course for export on init. The course id is taken from the dialog data when opened as a
     * modal, otherwise from the route params.
     */
    ngOnInit() {
        const dialogCourseId = this.dialogConfig?.data?.courseId;
        if (dialogCourseId !== undefined) {
            this.courseId = dialogCourseId;
            this.loadForCourse(this.courseId);
        } else {
            this.route.params.subscribe((params) => {
                this.courseId = params['courseId'];
                this.loadForCourse(this.courseId);
            });
        }
    }

    /**
     * Loads course for the given id and populates quiz exercises for the given course id.
     * All quiz questions are collected first and assigned in a single step (via forkJoin) so the view renders once
     * with the full list instead of growing as each quiz loads — which otherwise makes the dialog jump in height.
     * @param courseId Id of the course
     */
    private loadForCourse(courseId: number) {
        this.isLoading = true;
        this.courseService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
            // For the given course, get the list of all quiz exercises, then load each quiz's questions in parallel.
            this.quizExerciseService.findForCourse(courseId).subscribe({
                next: (res: HttpResponse<QuizExercise[]>) => {
                    const quizExercises = res.body!;
                    if (quizExercises.length === 0) {
                        this.questions = [];
                        this.isLoading = false;
                        return;
                    }
                    forkJoin(quizExercises.map((quizExercise) => this.quizExerciseService.find(quizExercise.id!))).subscribe({
                        next: (responses: HttpResponse<QuizExercise>[]) => {
                            const collected: QuizQuestion[] = [];
                            responses.forEach((response, index) => {
                                const quizExercise = quizExercises[index];
                                // reconnect course and exercise in case we need this information later
                                quizExercise.course = this.course;
                                response.body?.quizQuestions?.forEach((question) => {
                                    question.exercise = quizExercise;
                                    collected.push(question);
                                });
                            });
                            this.questions = collected;
                            this.isLoading = false;
                        },
                        error: (error: HttpErrorResponse) => {
                            this.isLoading = false;
                            onError(this.alertService, error);
                        },
                    });
                },
                error: (error: HttpErrorResponse) => {
                    this.isLoading = false;
                    onError(this.alertService, error);
                },
            });
        });
    }

    /**
     * Exports selected questions into json file.
     */
    exportQuiz() {
        this.quizExerciseService.exportQuiz(this.questions, false);
        // When shown as a modal, close it once the export has been triggered.
        this.dialogRef?.close();
    }

    /**
     * Closes the dialog with the {@link QUIZ_EXPORT_BACK} sentinel so the caller can reopen the manage-exercises modal.
     */
    back() {
        this.dialogRef?.close(QUIZ_EXPORT_BACK);
    }
}
