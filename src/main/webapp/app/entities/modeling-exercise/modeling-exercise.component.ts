import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-modeling-exercise',
    templateUrl: './modeling-exercise.component.html'
})
export class ModelingExerciseComponent implements OnInit, OnDestroy {
modelingExercises: ModelingExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private modelingExerciseService: ModelingExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.modelingExerciseService.query().subscribe(
            (res: HttpResponse<ModelingExercise[]>) => {
                this.modelingExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInModelingExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ModelingExercise) {
        return item.id;
    }
    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
