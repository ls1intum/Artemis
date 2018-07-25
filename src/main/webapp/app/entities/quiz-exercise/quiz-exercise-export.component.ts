import { Component, Inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';

import { NG1TRANSLATEPARTIALLOADER_SERVICE } from '../../shared/language/ng1-translate-partial-loader.service';
import { NG1TRANSLATE_SERVICE } from '../../shared/language/ng1-translate.service';

import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExercise } from './quiz-exercise.model';
import { JhiAlertService } from 'ng-jhipster';
import { Question } from '../question';
import { QuizExerciseComponent } from './quiz-exercise.component';

@Component({
    selector: 'jhi-quiz-exercise-export',
    templateUrl: './quiz-exercise-export.component.html'
})
export class QuizExerciseExportComponent implements OnInit {
    questions: Question[] = new Array(0);
    courseId: number;
    courseName: 'Some course';

    translateService: TranslateService;
    router: Router;

    constructor(private route: ActivatedRoute,
        private quizExerciseService: QuizExerciseService,
        private jhiAlertService: JhiAlertService,
        router: Router,
        translateService: TranslateService,
        @Inject(NG1TRANSLATE_SERVICE) private $translate: any,
        @Inject(NG1TRANSLATEPARTIALLOADER_SERVICE) private $translatePartialLoader: any) {
        this.router = router;
        this.translateService = translateService;
    }

    ngOnInit() {
        this.route.params.subscribe(params => {
            this.courseId = params['courseId'];
            this.loadForCourse(this.courseId);
        });
    }

    private loadForCourse(courseId) {
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
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    exportQuiz() {
        QuizExerciseComponent.exportQuiz(this.questions, false);
    }
}
