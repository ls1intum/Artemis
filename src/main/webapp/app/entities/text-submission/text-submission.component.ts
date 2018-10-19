import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ITextSubmission } from 'app/shared/model/text-submission.model';
import { Principal } from 'app/core';
import { TextSubmissionService } from './text-submission.service';

@Component({
    selector: 'jhi-text-submission',
    templateUrl: './text-submission.component.html'
})
export class TextSubmissionComponent implements OnInit, OnDestroy {
    textSubmissions: ITextSubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private textSubmissionService: TextSubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.textSubmissionService.query().subscribe(
            (res: HttpResponse<ITextSubmission[]>) => {
                this.textSubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInTextSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ITextSubmission) {
        return item.id;
    }

    registerChangeInTextSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('textSubmissionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
