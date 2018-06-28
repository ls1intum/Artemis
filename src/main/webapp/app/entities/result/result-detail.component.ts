import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Result } from './result.model';
import { ResultService } from './result.service';

@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html'
})
export class ResultDetailComponent implements OnInit, OnDestroy {

    result: Result;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private resultService: ResultService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInResults();
    }

    load(id) {
        this.resultService.find(id)
            .subscribe((resultResponse: HttpResponse<Result>) => {
                this.result = resultResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInResults() {
        this.eventSubscriber = this.eventManager.subscribe(
            'resultListModification',
            (response) => this.load(this.result.id)
        );
    }
}
