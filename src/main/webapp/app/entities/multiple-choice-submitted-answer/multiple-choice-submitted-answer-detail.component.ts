import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { MultipleChoiceSubmittedAnswer } from './multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-detail',
    templateUrl: './multiple-choice-submitted-answer-detail.component.html'
})
export class MultipleChoiceSubmittedAnswerDetailComponent implements OnInit, OnDestroy {

    multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInMultipleChoiceSubmittedAnswers();
    }

    load(id) {
        this.multipleChoiceSubmittedAnswerService.find(id)
            .subscribe((multipleChoiceSubmittedAnswerResponse: HttpResponse<MultipleChoiceSubmittedAnswer>) => {
                this.multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswerResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInMultipleChoiceSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe(
            'multipleChoiceSubmittedAnswerListModification',
            (response) => this.load(this.multipleChoiceSubmittedAnswer.id)
        );
    }
}
