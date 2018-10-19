import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDropLocationCounter } from 'app/shared/model/drop-location-counter.model';
import { DropLocationCounterService } from './drop-location-counter.service';

@Component({
    selector: 'jhi-drop-location-counter-delete-dialog',
    templateUrl: './drop-location-counter-delete-dialog.component.html'
})
export class DropLocationCounterDeleteDialogComponent {
    dropLocationCounter: IDropLocationCounter;

    constructor(
        private dropLocationCounterService: DropLocationCounterService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dropLocationCounterService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dropLocationCounterListModification',
                content: 'Deleted an dropLocationCounter'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drop-location-counter-delete-popup',
    template: ''
})
export class DropLocationCounterDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dropLocationCounter }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DropLocationCounterDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dropLocationCounter = dropLocationCounter;
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
