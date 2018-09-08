import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html'
})
export class QuizExerciseDetailComponent implements OnInit {
    quizExercise: IQuizExercise;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizExercise }) => {
            this.quizExercise = quizExercise;
        });
    }

    previousState() {
        window.history.back();
    }
}
