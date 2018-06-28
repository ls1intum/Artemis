import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ModelingSubmission } from './modeling-submission.model';
import { ModelingSubmissionService } from './modeling-submission.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-modeling-submission',
    templateUrl: './modeling-submission.component.html'
})
export class ModelingSubmissionComponent implements OnInit, OnDestroy {
modelingSubmissions: ModelingSubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private modelingSubmissionService: ModelingSubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.modelingSubmissionService.query().subscribe(
            (res: HttpResponse<ModelingSubmission[]>) => {
                this.modelingSubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInModelingSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ModelingSubmission) {
        return item.id;
    }
    registerChangeInModelingSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('modelingSubmissionListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
