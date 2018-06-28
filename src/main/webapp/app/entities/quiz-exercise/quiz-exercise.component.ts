import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-quiz-exercise',
    templateUrl: './quiz-exercise.component.html'
})
export class QuizExerciseComponent implements OnInit, OnDestroy {
quizExercises: QuizExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private quizExerciseService: QuizExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.quizExerciseService.query().subscribe(
            (res: HttpResponse<QuizExercise[]>) => {
                this.quizExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInQuizExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: QuizExercise) {
        return item.id;
    }
    registerChangeInQuizExercises() {
        this.eventSubscriber = this.eventManager.subscribe('quizExerciseListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
