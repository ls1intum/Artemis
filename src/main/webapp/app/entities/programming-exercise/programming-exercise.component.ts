import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IProgrammingExercise } from 'app/shared/model/programming-exercise.model';
import { Principal } from 'app/core';
import { ProgrammingExerciseService } from './programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html'
})
export class ProgrammingExerciseComponent implements OnInit, OnDestroy {
    programmingExercises: IProgrammingExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.programmingExerciseService.query().subscribe(
            (res: HttpResponse<IProgrammingExercise[]>) => {
                this.programmingExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInProgrammingExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IProgrammingExercise) {
        return item.id;
    }

    registerChangeInProgrammingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('programmingExerciseListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
