import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { AnswerOption } from './answer-option.model';
import { AnswerOptionService } from './answer-option.service';

@Component({
    selector: 'jhi-answer-option-detail',
    templateUrl: './answer-option-detail.component.html'
})
export class AnswerOptionDetailComponent implements OnInit, OnDestroy {

    answerOption: AnswerOption;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private answerOptionService: AnswerOptionService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInAnswerOptions();
    }

    load(id) {
        this.answerOptionService.find(id)
            .subscribe((answerOptionResponse: HttpResponse<AnswerOption>) => {
                this.answerOption = answerOptionResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInAnswerOptions() {
        this.eventSubscriber = this.eventManager.subscribe(
            'answerOptionListModification',
            (response) => this.load(this.answerOption.id)
        );
    }
}
