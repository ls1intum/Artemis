import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Course } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-quiz-exercise-export',
    templateUrl: './quiz-exercise-export.component.html',
    styleUrls: ['./quiz-exercise-export.component.scss', '../shared/quiz.scss'],
})
export class QuizExerciseExportComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private quizExerciseService = inject(QuizExerciseService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private translateService = inject(TranslateService);

    questions: QuizQuestion[] = new Array(0);
    courseId: number;
    course: Course;

    /**
     * Load the quizzes of the course for export on init.
     */
    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.loadForCourse(this.courseId);
        });
    }

    /**
     * Loads course for the given id and populates quiz exercises for the given course id
     * @param courseId Id of the course
     */
    private loadForCourse(courseId: number) {
        this.courseService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
            // For the given course, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise,
            this.quizExerciseService.findForCourse(courseId).subscribe({
                next: (res: HttpResponse<QuizExercise[]>) => {
                    const quizExercises = res.body!;
                    for (const quizExercise of quizExercises) {
                        // reconnect course and exercise in case we need this information later
                        quizExercise.course = this.course;
                        this.quizExerciseService.find(quizExercise.id!).subscribe((response: HttpResponse<QuizExercise>) => {
                            const quizExerciseResponse = response.body!;
                            quizExerciseResponse.quizQuestions!.forEach((question) => {
                                question.exercise = quizExercise;
                                this.questions.push(question);
                            });
                        });
                    }
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        });
    }

    /**
     * Exports selected questions into json file.
     */
    exportQuiz() {
        this.quizExerciseService.exportQuiz(this.questions, false);
    }
}
