import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { Principal } from 'app/core';
import { ExerciseResultService } from './exercise-result.service';

@Component({
    selector: 'jhi-exercise-result',
    templateUrl: './exercise-result.component.html'
})
export class ExerciseResultComponent implements OnInit, OnDestroy {
    exerciseResults: IExerciseResult[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private exerciseResultService: ExerciseResultService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.exerciseResultService.query().subscribe(
            (res: HttpResponse<IExerciseResult[]>) => {
                this.exerciseResults = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInExerciseResults();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IExerciseResult) {
        return item.id;
    }

    registerChangeInExerciseResults() {
        this.eventSubscriber = this.eventManager.subscribe('exerciseResultListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
