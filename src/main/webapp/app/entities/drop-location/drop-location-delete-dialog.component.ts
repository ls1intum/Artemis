import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDropLocation } from 'app/shared/model/drop-location.model';
import { DropLocationService } from './drop-location.service';

@Component({
    selector: 'jhi-drop-location-delete-dialog',
    templateUrl: './drop-location-delete-dialog.component.html'
})
export class DropLocationDeleteDialogComponent {
    dropLocation: IDropLocation;

    constructor(
        private dropLocationService: DropLocationService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dropLocationService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dropLocationListModification',
                content: 'Deleted an dropLocation'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drop-location-delete-popup',
    template: ''
})
export class DropLocationDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dropLocation }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DropLocationDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dropLocation = dropLocation;
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
