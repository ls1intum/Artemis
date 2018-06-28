import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Submission } from './submission.model';
import { SubmissionService } from './submission.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-submission',
    templateUrl: './submission.component.html'
})
export class SubmissionComponent implements OnInit, OnDestroy {
submissions: Submission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private submissionService: SubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.submissionService.query().subscribe(
            (res: HttpResponse<Submission[]>) => {
                this.submissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Submission) {
        return item.id;
    }
    registerChangeInSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('submissionListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
