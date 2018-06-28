import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Component({
    selector: 'jhi-drag-and-drop-question-detail',
    templateUrl: './drag-and-drop-question-detail.component.html'
})
export class DragAndDropQuestionDetailComponent implements OnInit, OnDestroy {

    dragAndDropQuestion: DragAndDropQuestion;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInDragAndDropQuestions();
    }

    load(id) {
        this.dragAndDropQuestionService.find(id)
            .subscribe((dragAndDropQuestionResponse: HttpResponse<DragAndDropQuestion>) => {
                this.dragAndDropQuestion = dragAndDropQuestionResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInDragAndDropQuestions() {
        this.eventSubscriber = this.eventManager.subscribe(
            'dragAndDropQuestionListModification',
            (response) => this.load(this.dragAndDropQuestion.id)
        );
    }
}
