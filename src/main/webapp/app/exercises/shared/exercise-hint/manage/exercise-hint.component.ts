import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';

import { TextHintService } from './text-hint.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faEye, faPlus, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TextHint } from 'app/entities/hestia/text-hint-model';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class TextHintComponent implements OnInit, OnDestroy {
    exerciseId: number;
    textHints: TextHint[];
    eventSubscriber: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    paramSub: Subscription;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    constructor(private route: ActivatedRoute, protected textHintService: TextHintService, private alertService: AlertService, protected eventManager: EventManager) {}

    /**
     * Subscribes to the route params to act on the currently selected exercise.
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.exerciseId = params['exerciseId'];
            this.loadAllByExerciseId();
            this.registerChangeInTextHints();
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
     * Load all text hints with the currently selected exerciseId (taken from route params).
     */
    loadAllByExerciseId() {
        this.textHintService
            .findByExerciseId(this.exerciseId)
            .pipe(
                filter((res: HttpResponse<TextHint[]>) => res.ok),
                map((res: HttpResponse<TextHint[]>) => res.body),
            )
            .subscribe({
                next: (res: TextHint[]) => {
                    this.textHints = res;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * Returns the track id of an text hint
     * @param index Index of the item
     * @param item Item for which to get the id
     */
    trackId(index: number, item: TextHint) {
        return item.id;
    }

    /**
     * (Re-)subscribe to the text hint list modification subscription
     */
    registerChangeInTextHints() {
        if (this.eventSubscriber) {
            this.eventSubscriber.unsubscribe();
        }
        this.eventSubscriber = this.eventManager.subscribe('textHintListModification', () => this.loadAllByExerciseId());
    }

    /**
     * Deletes text hint
     * @param textHintId the id of the text hint that we want to delete
     */
    deleteTextHint(textHintId: number) {
        this.textHintService.delete(textHintId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'textHintListModification',
                    content: 'Deleted an textHint',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
