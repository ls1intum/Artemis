import { Component, OnInit, Input, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { AccountService } from 'app/core';
import { ExerciseHintService } from './exercise-hint.service';
import { Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class ExerciseHintComponent implements OnInit, OnDestroy {
    exerciseId: number;
    exerciseHints: ExerciseHint[];
    eventSubscriber: Subscription;

    paramSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        protected exerciseHintService: ExerciseHintService,
        protected jhiAlertService: JhiAlertService,
        protected eventManager: JhiEventManager,
    ) {}

    /**
     * Subscribes to the route params to act on the currently selected exercise.
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseId = params['exerciseId'];
            this.loadAllByExerciseId();
            this.registerChangeInExerciseHints();
        });
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Load all exercise hints with the currently selected exerciseId (taken from route params).
     */
    loadAllByExerciseId() {
        this.exerciseHintService
            .findByExerciseId(this.exerciseId)
            .pipe(
                filter((res: HttpResponse<ExerciseHint[]>) => res.ok),
                map((res: HttpResponse<ExerciseHint[]>) => res.body),
            )
            .subscribe(
                (res: ExerciseHint[]) => {
                    this.exerciseHints = res;
                },
                (res: HttpErrorResponse) => this.onError(res.message),
            );
    }

    trackId(index: number, item: ExerciseHint) {
        return item.id;
    }

    registerChangeInExerciseHints() {
        if (this.eventSubscriber) {
            this.eventSubscriber.unsubscribe();
        }
        this.eventSubscriber = this.eventManager.subscribe('exerciseHintListModification', (response: any) => this.loadAllByExerciseId());
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
