import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ParticipationService } from './participation.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html'
})
export class ParticipationComponent implements OnInit, OnDestroy {
participations: Participation[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.participationService.query().subscribe(
            (res: HttpResponse<Participation[]>) => {
                this.participations = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInParticipations();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Participation) {
        return item.id;
    }
    registerChangeInParticipations() {
        this.eventSubscriber = this.eventManager.subscribe('participationListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
