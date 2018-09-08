import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ISubmission } from 'app/shared/model/submission.model';
import { Principal } from 'app/core';
import { SubmissionService } from './submission.service';

@Component({
    selector: 'jhi-submission',
    templateUrl: './submission.component.html'
})
export class SubmissionComponent implements OnInit, OnDestroy {
    submissions: ISubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private submissionService: SubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.submissionService.query().subscribe(
            (res: HttpResponse<ISubmission[]>) => {
                this.submissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ISubmission) {
        return item.id;
    }

    registerChangeInSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('submissionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
