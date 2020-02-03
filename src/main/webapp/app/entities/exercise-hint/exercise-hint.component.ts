import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription, Subject } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { onError } from 'app/utils/global.utils';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class ExerciseHintComponent implements OnInit, OnDestroy {
    exerciseId: number;
    exerciseHints: ExerciseHint[];
    eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    paramSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        protected exerciseHintService: ExerciseHintService,
        private jhiAlertService: JhiAlertService,
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
        this.dialogErrorSource.unsubscribe();
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
                (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
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

    /**
     * Deletes exercise hint
     * @param exerciseHintId the id of the exercise hint that we want to delete
     */
    deleteExerciseHint(exerciseHintId: number) {
        this.exerciseHintService.delete(exerciseHintId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'exerciseHintListModification',
                    content: 'Deleted an exerciseHint',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
