import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ITextExercise } from 'app/shared/model/text-exercise.model';
import { Principal } from 'app/core';
import { TextExerciseService } from './text-exercise.service';

@Component({
    selector: 'jhi-text-exercise',
    templateUrl: './text-exercise.component.html'
})
export class TextExerciseComponent implements OnInit, OnDestroy {
    textExercises: ITextExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private textExerciseService: TextExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.textExerciseService.query().subscribe(
            (res: HttpResponse<ITextExercise[]>) => {
                this.textExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInTextExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ITextExercise) {
        return item.id;
    }

    registerChangeInTextExercises() {
        this.eventSubscriber = this.eventManager.subscribe('textExerciseListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
