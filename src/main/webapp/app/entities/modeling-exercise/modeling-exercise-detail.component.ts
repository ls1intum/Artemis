import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise-detail',
    templateUrl: './modeling-exercise-detail.component.html'
})
export class ModelingExerciseDetailComponent implements OnInit, OnDestroy {

    modelingExercise: ModelingExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private modelingExerciseService: ModelingExerciseService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInModelingExercises();
    }

    load(id: number) {
        this.modelingExerciseService.find(id)
            .subscribe((modelingExerciseResponse: HttpResponse<ModelingExercise>) => {
                this.modelingExercise = modelingExerciseResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe(
            'modelingExerciseListModification',
            () => this.load(this.modelingExercise.id)
        );
    }
}
