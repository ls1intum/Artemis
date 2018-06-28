import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html'
})
export class QuizExerciseDetailComponent implements OnInit, OnDestroy {

    quizExercise: QuizExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private quizExerciseService: QuizExerciseService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInQuizExercises();
    }

    load(id) {
        this.quizExerciseService.find(id)
            .subscribe((quizExerciseResponse: HttpResponse<QuizExercise>) => {
                this.quizExercise = quizExerciseResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInQuizExercises() {
        this.eventSubscriber = this.eventManager.subscribe(
            'quizExerciseListModification',
            (response) => this.load(this.quizExercise.id)
        );
    }
}
