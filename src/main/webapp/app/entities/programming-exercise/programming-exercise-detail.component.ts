import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html'
})
export class ProgrammingExerciseDetailComponent implements OnInit, OnDestroy {

    programmingExercise: ProgrammingExercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private programmingExerciseService: ProgrammingExerciseService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInProgrammingExercises();
    }

    load(id) {
        this.programmingExerciseService.find(id)
            .subscribe((programmingExerciseResponse: HttpResponse<ProgrammingExercise>) => {
                this.programmingExercise = programmingExerciseResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInProgrammingExercises() {
        this.eventSubscriber = this.eventManager.subscribe(
            'programmingExerciseListModification',
            response => this.load(this.programmingExercise.id)
        );
    }
}
