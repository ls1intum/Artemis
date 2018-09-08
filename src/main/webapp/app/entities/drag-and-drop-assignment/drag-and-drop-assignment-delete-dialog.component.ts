import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';

@Component({
    selector: 'jhi-drag-and-drop-assignment-delete-dialog',
    templateUrl: './drag-and-drop-assignment-delete-dialog.component.html'
})
export class DragAndDropAssignmentDeleteDialogComponent {
    dragAndDropAssignment: IDragAndDropAssignment;

    constructor(
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropAssignmentService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dragAndDropAssignmentListModification',
                content: 'Deleted an dragAndDropAssignment'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drag-and-drop-assignment-delete-popup',
    template: ''
})
export class DragAndDropAssignmentDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropAssignment }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DragAndDropAssignmentDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dragAndDropAssignment = dragAndDropAssignment;
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
