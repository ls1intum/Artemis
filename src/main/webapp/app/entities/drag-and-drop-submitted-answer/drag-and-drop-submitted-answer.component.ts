import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';
import { Principal } from 'app/core';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer',
    templateUrl: './drag-and-drop-submitted-answer.component.html'
})
export class DragAndDropSubmittedAnswerComponent implements OnInit, OnDestroy {
    dragAndDropSubmittedAnswers: IDragAndDropSubmittedAnswer[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dragAndDropSubmittedAnswerService.query().subscribe(
            (res: HttpResponse<IDragAndDropSubmittedAnswer[]>) => {
                this.dragAndDropSubmittedAnswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropSubmittedAnswers();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDragAndDropSubmittedAnswer) {
        return item.id;
    }

    registerChangeInDragAndDropSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropSubmittedAnswerListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
