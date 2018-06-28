import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { SubmittedAnswer } from './submitted-answer.model';
import { SubmittedAnswerService } from './submitted-answer.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-submitted-answer',
    templateUrl: './submitted-answer.component.html'
})
export class SubmittedAnswerComponent implements OnInit, OnDestroy {
submittedAnswers: SubmittedAnswer[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private submittedAnswerService: SubmittedAnswerService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.submittedAnswerService.query().subscribe(
            (res: HttpResponse<SubmittedAnswer[]>) => {
                this.submittedAnswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInSubmittedAnswers();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: SubmittedAnswer) {
        return item.id;
    }
    registerChangeInSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe('submittedAnswerListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
