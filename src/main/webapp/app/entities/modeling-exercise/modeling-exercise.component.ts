import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IModelingExercise } from 'app/shared/model/modeling-exercise.model';
import { Principal } from 'app/core';
import { ModelingExerciseService } from './modeling-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise',
    templateUrl: './modeling-exercise.component.html'
})
export class ModelingExerciseComponent implements OnInit, OnDestroy {
    modelingExercises: IModelingExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private modelingExerciseService: ModelingExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.modelingExerciseService.query().subscribe(
            (res: HttpResponse<IModelingExercise[]>) => {
                this.modelingExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInModelingExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IModelingExercise) {
        return item.id;
    }

    registerChangeInModelingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('modelingExerciseListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
