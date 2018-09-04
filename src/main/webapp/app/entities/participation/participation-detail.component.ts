import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ParticipationService } from './participation.service';

@Component({
    selector: 'jhi-participation-detail',
    templateUrl: './participation-detail.component.html'
})
export class ParticipationDetailComponent implements OnInit, OnDestroy {

    participation: Participation;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private participationService: ParticipationService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
        this.registerChangeInParticipations();
    }

    load(id: number) {
        this.participationService.find(id)
            .subscribe((participationResponse: HttpResponse<Participation>) => {
                this.participation = participationResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInParticipations() {
        this.eventSubscriber = this.eventManager.subscribe(
            'participationListModification',
            () => this.load(this.participation.id)
        );
    }
}
