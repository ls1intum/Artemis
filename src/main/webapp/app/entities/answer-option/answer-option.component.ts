import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IAnswerOption } from 'app/shared/model/answer-option.model';
import { Principal } from 'app/core';
import { AnswerOptionService } from './answer-option.service';

@Component({
    selector: 'jhi-answer-option',
    templateUrl: './answer-option.component.html'
})
export class AnswerOptionComponent implements OnInit, OnDestroy {
    answerOptions: IAnswerOption[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private answerOptionService: AnswerOptionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.answerOptionService.query().subscribe(
            (res: HttpResponse<IAnswerOption[]>) => {
                this.answerOptions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInAnswerOptions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IAnswerOption) {
        return item.id;
    }

    registerChangeInAnswerOptions() {
        this.eventSubscriber = this.eventManager.subscribe('answerOptionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
