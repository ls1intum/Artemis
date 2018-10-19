import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ITextSubmission } from 'app/shared/model/text-submission.model';
import { TextSubmissionService } from './text-submission.service';

@Component({
    selector: 'jhi-text-submission-delete-dialog',
    templateUrl: './text-submission-delete-dialog.component.html'
})
export class TextSubmissionDeleteDialogComponent {
    textSubmission: ITextSubmission;

    constructor(
        private textSubmissionService: TextSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.textSubmissionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'textSubmissionListModification',
                content: 'Deleted an textSubmission'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-text-submission-delete-popup',
    template: ''
})
export class TextSubmissionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ textSubmission }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(TextSubmissionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.textSubmission = textSubmission;
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
