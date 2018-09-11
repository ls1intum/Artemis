import { Component, Inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

import { NG1TRANSLATEPARTIALLOADER_SERVICE } from '../../core/language/ng1-translate-partial-loader.service';
import { NG1TRANSLATE_SERVICE } from '../../core/language/ng1-translate.service';

import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExercise } from './quiz-exercise.model';
import { JhiAlertService } from 'ng-jhipster';
import { Question } from '../question';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { Course, CourseService } from '../course';

@Component({
    selector: 'jhi-quiz-exercise-export',
    templateUrl: './quiz-exercise-export.component.html'
})
export class QuizExerciseExportComponent implements OnInit {
    questions: Question[] = new Array(0);
    courseId: number;
    course: Course;

    translateService: TranslateService;
    router: Router;

    constructor(
        private route: ActivatedRoute,
        private quizExerciseService: QuizExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        router: Router,
        translateService: TranslateService,
        @Inject(NG1TRANSLATE_SERVICE) private $translate: any,
        @Inject(NG1TRANSLATEPARTIALLOADER_SERVICE) private $translatePartialLoader: any
    ) {
        this.router = router;
        this.translateService = translateService;
    }

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.courseId = params['courseId'];
            this.loadForCourse(this.courseId);
        });
    }

    /**
     * Loads course for the given id and populates quiz exercises for the given course id
     * @param courseId Id of the course
     */
    private loadForCourse(courseId: number) {
        this.courseService.find(this.courseId).subscribe(res => {
            this.course = res.body;
        });
        // For the given course, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise,
        this.quizExerciseService.findForCourse(courseId).subscribe(
            (res: HttpResponse<QuizExercise[]>) => {
                const quizExercises = res.body;
                for (const quizExercise of quizExercises) {
                    this.quizExerciseService.find(quizExercise.id).subscribe((response: HttpResponse<QuizExercise>) => {
                        const quizExerciseResponse = response.body;
                        for (const question of quizExerciseResponse.questions) {
                            question.exercise = quizExercise;
                            this.questions.push(question);
                        }
                    });
                }
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    /**
     * Handles when error is received
     * @param error Error
     */
    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, null);
    }

    /**
     * Exports selected questions into json file.
     */
    exportQuiz() {
        QuizExerciseComponent.exportQuiz(this.questions, false);
    }
}
