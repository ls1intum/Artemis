import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropSubmittedAnswer } from './drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-detail',
    templateUrl: './drag-and-drop-submitted-answer-detail.component.html'
})
export class DragAndDropSubmittedAnswerDetailComponent implements OnInit, OnDestroy {

    dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInDragAndDropSubmittedAnswers();
    }

    load(id) {
        this.dragAndDropSubmittedAnswerService.find(id)
            .subscribe((dragAndDropSubmittedAnswerResponse: HttpResponse<DragAndDropSubmittedAnswer>) => {
                this.dragAndDropSubmittedAnswer = dragAndDropSubmittedAnswerResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInDragAndDropSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe(
            'dragAndDropSubmittedAnswerListModification',
            (response) => this.load(this.dragAndDropSubmittedAnswer.id)
        );
    }
}
