import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IProgrammingSubmission } from 'app/shared/model/programming-submission.model';
import { Principal } from 'app/core';
import { ProgrammingSubmissionService } from './programming-submission.service';

@Component({
    selector: 'jhi-programming-submission',
    templateUrl: './programming-submission.component.html'
})
export class ProgrammingSubmissionComponent implements OnInit, OnDestroy {
    programmingSubmissions: IProgrammingSubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private programmingSubmissionService: ProgrammingSubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.programmingSubmissionService.query().subscribe(
            (res: HttpResponse<IProgrammingSubmission[]>) => {
                this.programmingSubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInProgrammingSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IProgrammingSubmission) {
        return item.id;
    }

    registerChangeInProgrammingSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('programmingSubmissionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
