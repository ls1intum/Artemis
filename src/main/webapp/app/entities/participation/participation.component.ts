import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ParticipationService } from './participation.service';
import { Principal } from '../../shared';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseService, ExerciseType } from '../exercise';

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
})
export class ParticipationComponent implements OnInit, OnDestroy {

    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    participations: Participation[];
    eventSubscriber: Subscription;
    paramSub: Subscription;
    exercise: Exercise;
    predicate: any;
    reverse: any;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal,
        private exerciseService: ExerciseService
    ) {
        this.reverse = true;
        this.predicate = 'id';
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe(params => {
            this.participationService.findAllParticipationsByExercise(params['exerciseId']).subscribe(res => {
                this.participations = res;
            });
            this.exerciseService.find(params['exerciseId']).subscribe(res => {
                this.exercise = res.body;
            });
        });
    }
    ngOnInit() {
        this.loadAll();
        this.registerChangeInParticipations();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Participation) {
        return item.id;
    }
    registerChangeInParticipations() {
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', response => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    callback() { }
}
