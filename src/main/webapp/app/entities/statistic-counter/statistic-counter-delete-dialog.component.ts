import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IStatisticCounter } from 'app/shared/model/statistic-counter.model';
import { StatisticCounterService } from './statistic-counter.service';

@Component({
    selector: 'jhi-statistic-counter-delete-dialog',
    templateUrl: './statistic-counter-delete-dialog.component.html'
})
export class StatisticCounterDeleteDialogComponent {
    statisticCounter: IStatisticCounter;

    constructor(
        private statisticCounterService: StatisticCounterService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.statisticCounterService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'statisticCounterListModification',
                content: 'Deleted an statisticCounter'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-statistic-counter-delete-popup',
    template: ''
})
export class StatisticCounterDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ statisticCounter }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(StatisticCounterDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.statisticCounter = statisticCounter;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    }
                );
            }, 0);
        });
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
