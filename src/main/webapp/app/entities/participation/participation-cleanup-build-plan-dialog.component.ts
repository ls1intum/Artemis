import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

import { Participation, ParticipationPopupService, ParticipationService } from '../participation';
import { ExerciseType } from '../exercise';

import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

@Component({
    selector: 'jhi-participation-cleanup-build-plan-dialog',
    templateUrl: './participation-cleanup-build-plan-dialog.component.html'
})
export class ParticipationCleanupBuildPlanDialogComponent implements OnInit {

    // make constants available to html for comparison
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;

    participation: Participation;

    constructor(
        private participationService: ParticipationService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmCleanup() {
        this.participationService.cleanupBuildPlan(this.participation).subscribe(() => {
            this.eventManager.broadcast({
                name: 'participationListModification',
                content: 'Cleanup the build plan of an participation'
            });
            this.activeModal.dismiss(true);
        });
    }

    ngOnInit(): void {

    }
}

@Component({
    selector: 'jhi-participation-delete-popup',
    template: ''
})
export class ParticipationCleanupBuildPlanPopupComponent implements OnInit, OnDestroy {

    routeSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        private participationPopupService: ParticipationPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.participationPopupService
                .open(ParticipationCleanupBuildPlanDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
