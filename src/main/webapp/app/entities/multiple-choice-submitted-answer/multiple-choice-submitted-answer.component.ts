import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IMultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';
import { Principal } from 'app/core';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer',
    templateUrl: './multiple-choice-submitted-answer.component.html'
})
export class MultipleChoiceSubmittedAnswerComponent implements OnInit, OnDestroy {
    multipleChoiceSubmittedAnswers: IMultipleChoiceSubmittedAnswer[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.multipleChoiceSubmittedAnswerService.query().subscribe(
            (res: HttpResponse<IMultipleChoiceSubmittedAnswer[]>) => {
                this.multipleChoiceSubmittedAnswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInMultipleChoiceSubmittedAnswers();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IMultipleChoiceSubmittedAnswer) {
        return item.id;
    }

    registerChangeInMultipleChoiceSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe('multipleChoiceSubmittedAnswerListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
