import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Participation, ParticipationPopupService, ParticipationService, StudentParticipation } from '../participation';
import { ExerciseType } from '../exercise';

import { Subscription } from 'rxjs/Subscription';
import { Submission } from 'app/entities/submission';

@Component({
    selector: 'jhi-participation-submission-delete-dialog',
    templateUrl: './participation-submission-delete-dialog.component.html',
})
export class ParticipationSubmissionDeleteDialogComponent implements OnInit {
    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    submission: Submission;

    constructor(private submissionService: ParticipationService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    // @loomila when this is working, we need to remove the delete method in the participation-submission.component.ts (commented out atm).
    //I comment it out for the moment and also the delete code here (so no further exercises need to be created) just check if the console.log is triggered
    confirmDelete(id: number) {
        console.log('delete with modal success');
        // this.submissionService.delete(id).subscribe(() => {
        //     this.eventManager.broadcast({
        //         name: 'submissionsModification',
        //         content: 'Deleted an submission',
        //     });
        //     this.activeModal.dismiss(true);
        // });
    }

    ngOnInit(): void {}
}

@Component({
    selector: 'jhi-participation-submission-delete-popup',
    template: '',
})
export class ParticipationSubmissionDeletePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    // @loomila we need to create our own participationPopupService -> participationSubmissionPopupService
    constructor(private route: ActivatedRoute, private participationPopupService: ParticipationPopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.participationPopupService.open(ParticipationSubmissionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
