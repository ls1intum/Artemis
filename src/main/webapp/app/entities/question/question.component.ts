import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Question } from './question.model';
import { QuestionService } from './question.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-question',
    templateUrl: './question.component.html'
})
export class QuestionComponent implements OnInit, OnDestroy {
questions: Question[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private questionService: QuestionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.questionService.query().subscribe(
            (res: HttpResponse<Question[]>) => {
                this.questions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInQuestions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Question) {
        return item.id;
    }
    registerChangeInQuestions() {
        this.eventSubscriber = this.eventManager.subscribe('questionListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
