import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IFileUploadSubmission } from 'app/shared/model/file-upload-submission.model';
import { FileUploadSubmissionService } from './file-upload-submission.service';

@Component({
    selector: 'jhi-file-upload-submission-delete-dialog',
    templateUrl: './file-upload-submission-delete-dialog.component.html'
})
export class FileUploadSubmissionDeleteDialogComponent {
    fileUploadSubmission: IFileUploadSubmission;

    constructor(
        private fileUploadSubmissionService: FileUploadSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.fileUploadSubmissionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'fileUploadSubmissionListModification',
                content: 'Deleted an fileUploadSubmission'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-file-upload-submission-delete-popup',
    template: ''
})
export class FileUploadSubmissionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ fileUploadSubmission }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(FileUploadSubmissionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.fileUploadSubmission = fileUploadSubmission;
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
