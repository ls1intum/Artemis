import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';
import { Principal } from 'app/core';
import { QuizExerciseService } from './quiz-exercise.service';

@Component({
    selector: 'jhi-quiz-exercise',
    templateUrl: './quiz-exercise.component.html'
})
export class QuizExerciseComponent implements OnInit, OnDestroy {
    quizExercises: IQuizExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private quizExerciseService: QuizExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.quizExerciseService.query().subscribe(
            (res: HttpResponse<IQuizExercise[]>) => {
                this.quizExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInQuizExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IQuizExercise) {
        return item.id;
    }

    registerChangeInQuizExercises() {
        this.eventSubscriber = this.eventManager.subscribe('quizExerciseListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
