import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';
import { Principal } from 'app/core';
import { DragAndDropQuestionStatisticService } from './drag-and-drop-question-statistic.service';

@Component({
    selector: 'jhi-drag-and-drop-question-statistic',
    templateUrl: './drag-and-drop-question-statistic.component.html'
})
export class DragAndDropQuestionStatisticComponent implements OnInit, OnDestroy {
    dragAndDropQuestionStatistics: IDragAndDropQuestionStatistic[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropQuestionStatisticService: DragAndDropQuestionStatisticService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dragAndDropQuestionStatisticService.query().subscribe(
            (res: HttpResponse<IDragAndDropQuestionStatistic[]>) => {
                this.dragAndDropQuestionStatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropQuestionStatistics();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDragAndDropQuestionStatistic) {
        return item.id;
    }

    registerChangeInDragAndDropQuestionStatistics() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropQuestionStatisticListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
