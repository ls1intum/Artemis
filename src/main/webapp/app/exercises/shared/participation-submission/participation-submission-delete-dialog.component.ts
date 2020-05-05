import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Subscription } from 'rxjs/Subscription';
import { ParticipationSubmissionPopupService } from 'app/exercises/shared/participation-submission/participation-submission-popup.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

@Component({
    selector: 'jhi-participation-submission-delete-dialog',
    templateUrl: './participation-submission-delete-dialog.component.html',
})
export class ParticipationSubmissionDeleteDialogComponent implements OnInit {
    submissionId: number;

    constructor(private submissionService: SubmissionService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    /**
     * Close modal window.
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Delete submission and close modal window.
     * @param { number } id - Id of submission that is deleted.
     */
    confirmDelete(id: number) {
        this.submissionService.delete(id).subscribe(() => {
            this.eventManager.broadcast({
                name: 'submissionsModification',
                content: 'Deleted a submission',
            });
            this.activeModal.dismiss(true);
        });
    }

    /**
     * Empty initialization.
     */
    ngOnInit(): void {}
}

@Component({
    selector: 'jhi-participation-submission-delete-popup',
    template: '',
})
// TODO: replace this with our new delete dialog
export class ParticipationSubmissionDeletePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;
    constructor(private route: ActivatedRoute, private participationSubmissionPopupService: ParticipationSubmissionPopupService) {}

    /**
     * Subscribe to route.params and open new popup window with participationId and submissionId
     */
    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.participationSubmissionPopupService.open(ParticipationSubmissionDeleteDialogComponent as Component, params['participationId'], params['submissionId']);
        });
    }

    /**
     * Unsubscribe from all subscriptions.
     */
    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
