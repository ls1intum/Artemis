import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';

import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';

@Component({
    selector: 'jhi-exercise-hint',
    templateUrl: './exercise-hint.component.html',
})
export class ExerciseHintComponent implements OnInit, OnDestroy {
    exerciseId: number;
    exerciseHints: IExerciseHint[];
    eventSubscriber: Subscription;

    paramSub: Subscription;

    constructor(private route: ActivatedRoute, protected exerciseHintService: ExerciseHintService, protected jhiAlertService: JhiAlertService) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseId = params['exerciseId'];
            this.loadAllByExerciseId();
        });
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    loadAllByExerciseId() {
        this.exerciseHintService
            .findByExerciseId(this.exerciseId)
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

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
