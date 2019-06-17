import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ParticipationService } from './participation.service';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseType } from '../exercise';
import { ExerciseService } from '../exercise/exercise.service';
import { HttpErrorResponse } from '@angular/common/http';

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
    predicate: string;
    reverse: boolean;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
    ) {
        this.reverse = true;
        this.predicate = 'id';
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseService.find(params['exerciseId']).subscribe(exerciseResponse => {
                this.exercise = exerciseResponse.body;
                this.participationService.findAllParticipationsByExercise(params['exerciseId']).subscribe(participationsResponse => {
                    this.participations = participationsResponse.body;
                });
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
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', () => this.loadAll());
    }

    addPresentation(participation: Participation) {
        participation.presentationScore = 1;
        this.participationService.update(participation).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.participation.addPresentation.error');
            },
        );
    }

    removePresentation(participation: Participation) {
        participation.presentationScore = 0;
        this.participationService.update(participation).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.participation.removePresentation.error');
            },
        );
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    callback() {}
}
