import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { DropLocation } from './drop-location.model';
import { DropLocationService } from './drop-location.service';

@Component({
    selector: 'jhi-drop-location-detail',
    templateUrl: './drop-location-detail.component.html'
})
export class DropLocationDetailComponent implements OnInit, OnDestroy {

    dropLocation: DropLocation;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private dropLocationService: DropLocationService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInDropLocations();
    }

    load(id) {
        this.dropLocationService.find(id)
            .subscribe((dropLocationResponse: HttpResponse<DropLocation>) => {
                this.dropLocation = dropLocationResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInDropLocations() {
        this.eventSubscriber = this.eventManager.subscribe(
            'dropLocationListModification',
            (response) => this.load(this.dropLocation.id)
        );
    }
}
