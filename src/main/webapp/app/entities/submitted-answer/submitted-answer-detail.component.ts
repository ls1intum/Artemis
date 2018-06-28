import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { SubmittedAnswer } from './submitted-answer.model';
import { SubmittedAnswerService } from './submitted-answer.service';

@Component({
    selector: 'jhi-submitted-answer-detail',
    templateUrl: './submitted-answer-detail.component.html'
})
export class SubmittedAnswerDetailComponent implements OnInit, OnDestroy {

    submittedAnswer: SubmittedAnswer;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private submittedAnswerService: SubmittedAnswerService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInSubmittedAnswers();
    }

    load(id) {
        this.submittedAnswerService.find(id)
            .subscribe((submittedAnswerResponse: HttpResponse<SubmittedAnswer>) => {
                this.submittedAnswer = submittedAnswerResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe(
            'submittedAnswerListModification',
            (response) => this.load(this.submittedAnswer.id)
        );
    }
}
