import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Participation, ParticipationPopupService, ParticipationService } from '../participation';
import { ExerciseType } from '../exercise';

@Component({
    selector: 'jhi-participation-delete-dialog',
    templateUrl: './participation-delete-dialog.component.html'
})
export class ParticipationDeleteDialogComponent implements OnInit {

    // make constants available to html for comparison
    readonly QUIZ = ExerciseType.QUIZ;
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly MODELING = ExerciseType.MODELING;

    participation: Participation;
    deleteBuildPlan: boolean;
    deleteRepository: boolean;

    constructor(
        private participationService: ParticipationService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number, deleteBuildPlan: boolean, deleteRepository: boolean) {
        this.participationService.delete(id, {deleteBuildPlan, deleteRepository}).subscribe(() => {
            this.eventManager.broadcast({
                name: 'participationListModification',
                content: 'Deleted an participation'
            });
            this.activeModal.dismiss(true);
        });
    }

    ngOnInit(): void {
        this.deleteBuildPlan = this.participation.exercise.type === ExerciseType.PROGRAMMING;
        this.deleteRepository = this.participation.exercise.type === ExerciseType.PROGRAMMING;
    }
}

@Component({
    selector: 'jhi-participation-delete-popup',
    template: ''
})
export class ParticipationDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private participationPopupService: ParticipationPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.participationPopupService
                .open(ParticipationDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
