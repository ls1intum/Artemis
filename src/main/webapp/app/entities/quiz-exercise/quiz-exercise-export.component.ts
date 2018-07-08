import { Component, OnInit } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { QuizExercise } from './quiz-exercise.model';
import { JhiAlertService } from 'ng-jhipster';
import { Question } from '../question';

@Component({
  selector: 'jhi-quiz-exercise-export',
  templateUrl: './quiz-exercise-export.component.html'
})
export class QuizExerciseExportComponent implements OnInit {
  quizExercises: QuizExercise[];
  questions: Question[];
  courseId: number;

  repository: QuizExerciseService;
  router: Router;

  constructor(private route: ActivatedRoute,
    private quizExerciseService: QuizExerciseService,
    private jhiAlertService: JhiAlertService,
    router: Router) {
    this.router = router;
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
        this.quizExercises = res.body;
        for (let quizExercise of this.quizExercises) {
          this.quizExerciseService.find(quizExercise.id).subscribe((response: HttpResponse<QuizExercise>) => {
            this.questions.concat(quizExercise.questions);
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
