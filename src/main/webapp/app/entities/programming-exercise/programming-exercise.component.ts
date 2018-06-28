import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-programming-exercise',
    templateUrl: './programming-exercise.component.html'
})
export class ProgrammingExerciseComponent implements OnInit, OnDestroy {
programmingExercises: ProgrammingExercise[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.programmingExerciseService.query().subscribe(
            (res: HttpResponse<ProgrammingExercise[]>) => {
                this.programmingExercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInProgrammingExercises();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ProgrammingExercise) {
        return item.id;
    }
    registerChangeInProgrammingExercises() {
        this.eventSubscriber = this.eventManager.subscribe('programmingExerciseListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
