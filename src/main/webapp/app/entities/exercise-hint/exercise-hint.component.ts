import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { AccountService } from 'app/core';
import { ExerciseHintService } from './exercise-hint.service';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class ExerciseHintComponent implements OnInit, OnDestroy {
    exerciseHints: IExerciseHint[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        protected exerciseHintService: ExerciseHintService,
        protected jhiAlertService: JhiAlertService,
        protected eventManager: JhiEventManager,
        protected accountService: AccountService,
    ) {}

    loadAll() {
        this.exerciseHintService
            .query()
            .pipe(
                filter((res: HttpResponse<IExerciseHint[]>) => res.ok),
                map((res: HttpResponse<IExerciseHint[]>) => res.body),
            )
            .subscribe(
                (res: IExerciseHint[]) => {
                    this.exerciseHints = res;
                },
                (res: HttpErrorResponse) => this.onError(res.message),
            );
    }

    ngOnInit() {
        this.loadAll();
        this.accountService.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInExerciseHints();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IExerciseHint) {
        return item.id;
    }

    registerChangeInExerciseHints() {
        this.eventSubscriber = this.eventManager.subscribe('exerciseHintListModification', (response: any) => this.loadAll());
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
