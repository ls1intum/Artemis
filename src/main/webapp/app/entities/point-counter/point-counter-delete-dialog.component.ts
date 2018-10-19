import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IPointCounter } from 'app/shared/model/point-counter.model';
import { PointCounterService } from './point-counter.service';

@Component({
    selector: 'jhi-point-counter-delete-dialog',
    templateUrl: './point-counter-delete-dialog.component.html'
})
export class PointCounterDeleteDialogComponent {
    pointCounter: IPointCounter;

    constructor(
        private pointCounterService: PointCounterService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.pointCounterService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'pointCounterListModification',
                content: 'Deleted an pointCounter'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-point-counter-delete-popup',
    template: ''
})
export class PointCounterDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ pointCounter }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(PointCounterDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.pointCounter = pointCounter;
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
