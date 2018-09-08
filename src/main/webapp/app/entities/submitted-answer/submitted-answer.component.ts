import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ISubmittedAnswer } from 'app/shared/model/submitted-answer.model';
import { Principal } from 'app/core';
import { SubmittedAnswerService } from './submitted-answer.service';

@Component({
    selector: 'jhi-submitted-answer',
    templateUrl: './submitted-answer.component.html'
})
export class SubmittedAnswerComponent implements OnInit, OnDestroy {
    submittedAnswers: ISubmittedAnswer[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private submittedAnswerService: SubmittedAnswerService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.submittedAnswerService.query().subscribe(
            (res: HttpResponse<ISubmittedAnswer[]>) => {
                this.submittedAnswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInSubmittedAnswers();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ISubmittedAnswer) {
        return item.id;
    }

    registerChangeInSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe('submittedAnswerListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
