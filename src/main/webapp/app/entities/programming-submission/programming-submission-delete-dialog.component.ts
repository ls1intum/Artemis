import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IProgrammingSubmission } from 'app/shared/model/programming-submission.model';
import { ProgrammingSubmissionService } from './programming-submission.service';

@Component({
    selector: 'jhi-programming-submission-delete-dialog',
    templateUrl: './programming-submission-delete-dialog.component.html'
})
export class ProgrammingSubmissionDeleteDialogComponent {
    programmingSubmission: IProgrammingSubmission;

    constructor(
        private programmingSubmissionService: ProgrammingSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.programmingSubmissionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'programmingSubmissionListModification',
                content: 'Deleted an programmingSubmission'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-programming-submission-delete-popup',
    template: ''
})
export class ProgrammingSubmissionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingSubmission }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ProgrammingSubmissionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.programmingSubmission = programmingSubmission;
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
