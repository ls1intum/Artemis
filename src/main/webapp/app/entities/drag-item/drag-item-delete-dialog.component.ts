import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DragItem } from './drag-item.model';
import { DragItemPopupService } from './drag-item-popup.service';
import { DragItemService } from './drag-item.service';

@Component({
    selector: 'jhi-drag-item-delete-dialog',
    templateUrl: './drag-item-delete-dialog.component.html'
})
export class DragItemDeleteDialogComponent {

    dragItem: DragItem;

    constructor(
        private dragItemService: DragItemService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragItemService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'dragItemListModification',
                content: 'Deleted an dragItem'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drag-item-delete-popup',
    template: ''
})
export class DragItemDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragItemPopupService: DragItemPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.dragItemPopupService
                .open(DragItemDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
