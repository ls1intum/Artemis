import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { Principal } from 'app/core';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Component({
    selector: 'jhi-drag-and-drop-question',
    templateUrl: './drag-and-drop-question.component.html'
})
export class DragAndDropQuestionComponent implements OnInit, OnDestroy {
    dragAndDropQuestions: IDragAndDropQuestion[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dragAndDropQuestionService.query().subscribe(
            (res: HttpResponse<IDragAndDropQuestion[]>) => {
                this.dragAndDropQuestions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropQuestions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDragAndDropQuestion) {
        return item.id;
    }

    registerChangeInDragAndDropQuestions() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropQuestionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
