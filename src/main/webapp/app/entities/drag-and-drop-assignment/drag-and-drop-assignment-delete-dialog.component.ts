import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropAssignment } from './drag-and-drop-assignment.model';
import { DragAndDropAssignmentPopupService } from './drag-and-drop-assignment-popup.service';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';

@Component({
    selector: 'jhi-drag-and-drop-assignment-delete-dialog',
    templateUrl: './drag-and-drop-assignment-delete-dialog.component.html'
})
export class DragAndDropAssignmentDeleteDialogComponent {

    dragAndDropAssignment: DragAndDropAssignment;

    constructor(
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropAssignmentService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragAndDropAssignmentPopupService: DragAndDropAssignmentPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.dragAndDropAssignmentPopupService
                .open(DragAndDropAssignmentDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
