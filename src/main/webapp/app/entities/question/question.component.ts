import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IQuestion } from 'app/shared/model/question.model';
import { Principal } from 'app/core';
import { QuestionService } from './question.service';

@Component({
    selector: 'jhi-question',
    templateUrl: './question.component.html'
})
export class QuestionComponent implements OnInit, OnDestroy {
    questions: IQuestion[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private questionService: QuestionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.questionService.query().subscribe(
            (res: HttpResponse<IQuestion[]>) => {
                this.questions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInQuestions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IQuestion) {
        return item.id;
    }

    registerChangeInQuestions() {
        this.eventSubscriber = this.eventManager.subscribe('questionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
