import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Exercise } from './exercise.model';
import { ExerciseService } from './exercise.service';

@Component({
    selector: 'jhi-exercise-detail',
    templateUrl: './exercise-detail.component.html'
})
export class ExerciseDetailComponent implements OnInit, OnDestroy {

    exercise: Exercise;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInExercises();
    }

    load(id) {
        this.exerciseService.find(id)
            .subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                this.exercise = exerciseResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInExercises() {
        this.eventSubscriber = this.eventManager.subscribe(
            'exerciseListModification',
            (response) => this.load(this.exercise.id)
        );
    }
}
