import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { MultipleChoiceQuestion } from './multiple-choice-question.model';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';

@Component({
    selector: 'jhi-multiple-choice-question-detail',
    templateUrl: './multiple-choice-question-detail.component.html'
})
export class MultipleChoiceQuestionDetailComponent implements OnInit, OnDestroy {

    multipleChoiceQuestion: MultipleChoiceQuestion;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private multipleChoiceQuestionService: MultipleChoiceQuestionService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInMultipleChoiceQuestions();
    }

    load(id) {
        this.multipleChoiceQuestionService.find(id)
            .subscribe((multipleChoiceQuestionResponse: HttpResponse<MultipleChoiceQuestion>) => {
                this.multipleChoiceQuestion = multipleChoiceQuestionResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInMultipleChoiceQuestions() {
        this.eventSubscriber = this.eventManager.subscribe(
            'multipleChoiceQuestionListModification',
            (response) => this.load(this.multipleChoiceQuestion.id)
        );
    }
}
