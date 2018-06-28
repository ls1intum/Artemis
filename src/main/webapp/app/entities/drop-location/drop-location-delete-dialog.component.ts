import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DropLocation } from './drop-location.model';
import { DropLocationPopupService } from './drop-location-popup.service';
import { DropLocationService } from './drop-location.service';

@Component({
    selector: 'jhi-drop-location-delete-dialog',
    templateUrl: './drop-location-delete-dialog.component.html'
})
export class DropLocationDeleteDialogComponent {

    dropLocation: DropLocation;

    constructor(
        private dropLocationService: DropLocationService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dropLocationService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dropLocationPopupService: DropLocationPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.dropLocationPopupService
                .open(DropLocationDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
