import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';
import { DragAndDropQuestionStatisticService } from './drag-and-drop-question-statistic.service';

@Component({
    selector: 'jhi-drag-and-drop-question-statistic-delete-dialog',
    templateUrl: './drag-and-drop-question-statistic-delete-dialog.component.html'
})
export class DragAndDropQuestionStatisticDeleteDialogComponent {
    dragAndDropQuestionStatistic: IDragAndDropQuestionStatistic;

    constructor(
        private dragAndDropQuestionStatisticService: DragAndDropQuestionStatisticService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropQuestionStatisticService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dragAndDropQuestionStatisticListModification',
                content: 'Deleted an dragAndDropQuestionStatistic'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drag-and-drop-question-statistic-delete-popup',
    template: ''
})
export class DragAndDropQuestionStatisticDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropQuestionStatistic }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DragAndDropQuestionStatisticDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dragAndDropQuestionStatistic = dragAndDropQuestionStatistic;
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
