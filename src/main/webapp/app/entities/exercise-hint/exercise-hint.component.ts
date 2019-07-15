import { Component, OnInit, Input, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { AccountService } from 'app/core';
import { ExerciseHintService } from './exercise-hint.service';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class ExerciseHintComponent implements OnInit, OnDestroy {
    exerciseId: number;
    exerciseHints: IExerciseHint[];
    eventSubscriber: Subscription;

    paramSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        protected exerciseHintService: ExerciseHintService,
        protected jhiAlertService: JhiAlertService,
        protected eventManager: JhiEventManager,
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseId = params['exerciseId'];
            this.loadAll();
            this.registerChangeInExerciseHints();
        });
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

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
