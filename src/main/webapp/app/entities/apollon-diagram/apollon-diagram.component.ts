import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IApollonDiagram } from 'app/shared/model/apollon-diagram.model';
import { Principal } from 'app/core';
import { ApollonDiagramService } from './apollon-diagram.service';

@Component({
    selector: 'jhi-apollon-diagram',
    templateUrl: './apollon-diagram.component.html'
})
export class ApollonDiagramComponent implements OnInit, OnDestroy {
    apollonDiagrams: IApollonDiagram[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.apollonDiagramService.query().subscribe(
            (res: HttpResponse<IApollonDiagram[]>) => {
                this.apollonDiagrams = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInApollonDiagrams();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IApollonDiagram) {
        return item.id;
    }

    registerChangeInApollonDiagrams() {
        this.eventSubscriber = this.eventManager.subscribe('apollonDiagramListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
