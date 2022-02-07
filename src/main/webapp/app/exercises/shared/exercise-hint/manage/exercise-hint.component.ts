import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faEye, faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';

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

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    constructor(private route: ActivatedRoute, protected exerciseHintService: ExerciseHintService, private alertService: AlertService, protected eventManager: EventManager) {}

    /**
     * Subscribes to the route params to act on the currently selected exercise.
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = params['exerciseId'];
            this.loadAllByExerciseId();
            this.registerChangeInExerciseHints();
        });
    }

    /**
     * Unsubscribe from subscriptions
     */
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
            .subscribe({
                next: (res: ExerciseHint[]) => {
                    this.exerciseHints = res;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * Returns the track id of an exercise hint
     * @param index Index of the item
     * @param item Item for which to get the id
     */
    trackId(index: number, item: ExerciseHint) {
        return item.id;
    }

    /**
     * (Re-)subscribe to the exercise hint list modification subscription
     */
    registerChangeInExerciseHints() {
        if (this.eventSubscriber) {
            this.eventSubscriber.unsubscribe();
        }
        this.eventSubscriber = this.eventManager.subscribe('exerciseHintListModification', () => this.loadAllByExerciseId());
    }

    /**
     * Deletes exercise hint
     * @param exerciseHintId the id of the exercise hint that we want to delete
     */
    deleteExerciseHint(exerciseHintId: number) {
        this.exerciseHintService.delete(exerciseHintId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'exerciseHintListModification',
                    content: 'Deleted an exerciseHint',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
