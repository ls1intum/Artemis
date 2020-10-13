import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ISubmission } from 'app/shared/model/submission.model';
import { SubmissionService } from './submission.service';

@Component({
    templateUrl: './submission-delete-dialog.component.html',
})
export class SubmissionDeleteDialogComponent {
    submission?: ISubmission;

    constructor(protected submissionService: SubmissionService, public activeModal: NgbActiveModal, protected eventManager: JhiEventManager) {}

    cancel(): void {
        this.activeModal.dismiss();
    }

    confirmDelete(id: number): void {
        this.submissionService.delete(id).subscribe(() => {
            this.eventManager.broadcast('submissionListModification');
            this.activeModal.close();
        });
    }
}
