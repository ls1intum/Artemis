import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { AnswerOption } from './answer-option.model';
import { AnswerOptionService } from './answer-option.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-answer-option',
    templateUrl: './answer-option.component.html'
})
export class AnswerOptionComponent implements OnInit, OnDestroy {
answerOptions: AnswerOption[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private answerOptionService: AnswerOptionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.answerOptionService.query().subscribe(
            (res: HttpResponse<AnswerOption[]>) => {
                this.answerOptions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInAnswerOptions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: AnswerOption) {
        return item.id;
    }
    registerChangeInAnswerOptions() {
        this.eventSubscriber = this.eventManager.subscribe('answerOptionListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
