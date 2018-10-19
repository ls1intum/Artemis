import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';
import { DragAndDropMappingService } from './drag-and-drop-mapping.service';

@Component({
    selector: 'jhi-drag-and-drop-mapping-delete-dialog',
    templateUrl: './drag-and-drop-mapping-delete-dialog.component.html'
})
export class DragAndDropMappingDeleteDialogComponent {
    dragAndDropMapping: IDragAndDropMapping;

    constructor(
        private dragAndDropMappingService: DragAndDropMappingService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropMappingService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dragAndDropMappingListModification',
                content: 'Deleted an dragAndDropMapping'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drag-and-drop-mapping-delete-popup',
    template: ''
})
export class DragAndDropMappingDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropMapping }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DragAndDropMappingDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dragAndDropMapping = dragAndDropMapping;
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
