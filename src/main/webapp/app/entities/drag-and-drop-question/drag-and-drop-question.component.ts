import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-drag-and-drop-question',
    templateUrl: './drag-and-drop-question.component.html'
})
export class DragAndDropQuestionComponent implements OnInit, OnDestroy {
dragAndDropQuestions: DragAndDropQuestion[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.dragAndDropQuestionService.query().subscribe(
            (res: HttpResponse<DragAndDropQuestion[]>) => {
                this.dragAndDropQuestions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropQuestions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: DragAndDropQuestion) {
        return item.id;
    }
    registerChangeInDragAndDropQuestions() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropQuestionListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
