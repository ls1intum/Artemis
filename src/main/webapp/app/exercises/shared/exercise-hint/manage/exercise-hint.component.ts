import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { ExerciseHintService } from '../shared/exercise-hint.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faArrowsRotate, faCode, faEye, faFont, faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class ExerciseHintComponent implements OnInit, OnDestroy {
    readonly HintType = HintType;
    ExerciseType = ExerciseType;
    exercise: ProgrammingExercise;
    exerciseHints: ExerciseHint[];
    containsCodeHints: boolean;
    eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    paramSub: Subscription;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faText = faFont;
    faCode = faCode;
    faArrowsRotate = faArrowsRotate;

    constructor(private route: ActivatedRoute, protected exerciseHintService: ExerciseHintService, private alertService: AlertService, protected eventManager: EventManager) {}

    /**
     * Subscribes to the route params to act on the currently selected exercise.
     */
    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;
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
            .findByExerciseId(this.exercise.id!)
            .pipe(
                filter((res: HttpResponse<ExerciseHint[]>) => res.ok),
                map((res: HttpResponse<ExerciseHint[]>) => res.body),
            )
            .subscribe({
                next: (res: ExerciseHint[]) => {
                    this.exerciseHints = res;
                    this.containsCodeHints = this.exerciseHints?.some((hint) => hint.type === HintType.CODE);
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
        this.exerciseHintService.delete(this.exercise.id!, exerciseHintId).subscribe({
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
