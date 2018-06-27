import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ExerciseParticipationService, ParticipationService } from './participation.service';
import { Principal } from '../../shared';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseService } from '../exercise';

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
    providers: [ExerciseParticipationService]
})
export class ParticipationComponent implements OnInit, OnDestroy {
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
        private exerciseParticipationService: ExerciseParticipationService,
        private exerciseService: ExerciseService
    ) {
        this.reverse = true;
        this.predicate = 'id';
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseParticipationService.findByExercise(params['exerciseId']).subscribe(res => {
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
