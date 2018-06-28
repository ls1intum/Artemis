import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { MultipleChoiceSubmittedAnswer } from './multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer',
    templateUrl: './multiple-choice-submitted-answer.component.html'
})
export class MultipleChoiceSubmittedAnswerComponent implements OnInit, OnDestroy {
multipleChoiceSubmittedAnswers: MultipleChoiceSubmittedAnswer[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.multipleChoiceSubmittedAnswerService.query().subscribe(
            (res: HttpResponse<MultipleChoiceSubmittedAnswer[]>) => {
                this.multipleChoiceSubmittedAnswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInMultipleChoiceSubmittedAnswers();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: MultipleChoiceSubmittedAnswer) {
        return item.id;
    }
    registerChangeInMultipleChoiceSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe('multipleChoiceSubmittedAnswerListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
