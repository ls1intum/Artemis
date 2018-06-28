import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DragAndDropSubmittedAnswer } from './drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer',
    templateUrl: './drag-and-drop-submitted-answer.component.html'
})
export class DragAndDropSubmittedAnswerComponent implements OnInit, OnDestroy {
dragAndDropSubmittedAnswers: DragAndDropSubmittedAnswer[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.dragAndDropSubmittedAnswerService.query().subscribe(
            (res: HttpResponse<DragAndDropSubmittedAnswer[]>) => {
                this.dragAndDropSubmittedAnswers = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropSubmittedAnswers();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: DragAndDropSubmittedAnswer) {
        return item.id;
    }
    registerChangeInDragAndDropSubmittedAnswers() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropSubmittedAnswerListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
