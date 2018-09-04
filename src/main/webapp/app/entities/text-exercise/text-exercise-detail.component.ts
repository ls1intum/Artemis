import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExerciseService } from './text-exercise.service';

@Component({
    selector: 'jhi-text-exercise-detail',
    templateUrl: './text-exercise-detail.component.html'
})
export class TextExerciseDetailComponent implements OnInit, OnDestroy {

    textExercise: TextExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private textExerciseService: TextExerciseService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInTextExercises();
    }

    load(id: number) {
        this.textExerciseService.find(id)
            .subscribe((textExerciseResponse: HttpResponse<TextExercise>) => {
                this.textExercise = textExerciseResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInTextExercises() {
        this.eventSubscriber = this.eventManager.subscribe(
            'textExerciseListModification',
            () => this.load(this.textExercise.id)
        );
    }
}
