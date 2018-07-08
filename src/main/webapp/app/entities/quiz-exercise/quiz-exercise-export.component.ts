import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs/Subscription';

import { NG1TRANSLATEPARTIALLOADER_SERVICE } from '../../shared/language/ng1-translate-partial-loader.service';
import { NG1TRANSLATE_SERVICE } from '../../shared/language/ng1-translate.service';

import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExercise } from './quiz-exercise.model';
import { JhiAlertService } from 'ng-jhipster';
import { Question } from '../question';
import { EMAIL_ALREADY_USED_TYPE } from '../../shared';

@Component({
  selector: 'jhi-quiz-exercise-export',
  templateUrl: './quiz-exercise-export.component.html'
})
export class QuizExerciseExportComponent implements OnInit, OnDestroy {
  questions: Question[] = new Array(0);
  courseId: number;

  private subscription: Subscription;
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
    this.subscription = this.route.params.subscribe(params => {
      this.courseId = params['courseId'];
      this.loadForCourse(this.courseId);
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  private loadForCourse(courseId) {
    this.quizExerciseService.findForCourse(courseId).subscribe(
      (res: HttpResponse<QuizExercise[]>) => {
        const quizExercises = res.body;
        for (const quizExercise of quizExercises) {
          this.quizExerciseService.find(quizExercise.id).subscribe((response: HttpResponse<QuizExercise>) => {
            const question = response.body;
            this.questions.push(question);
          });
        }
      },
      (res: HttpErrorResponse) => this.onError(res.message)
    );
  }

  private onError(error) {
    this.jhiAlertService.error(error.message, null, null);
  }
}
