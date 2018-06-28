import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Result } from './result.model';
import { ResultPopupService } from './result-popup.service';
import { ResultService } from './result.service';

@Component({
    selector: 'jhi-result-delete-dialog',
    templateUrl: './result-delete-dialog.component.html'
})
export class ResultDeleteDialogComponent {

    result: Result;

    constructor(
        private resultService: ResultService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.resultService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'resultListModification',
                content: 'Deleted an result'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-result-delete-popup',
    template: ''
})
export class ResultDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private resultPopupService: ResultPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.resultPopupService
                .open(ResultDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
