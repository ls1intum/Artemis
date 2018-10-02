import { Component, OnDestroy, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from './exercise.model';
import { ActivatedRoute } from '@angular/router';
import { ExercisePopupService } from './exercise-popup.service';
import { LtiConfiguration } from 'app/entities/lti-configuration';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-exercise-lti-configuration-dialog',
    templateUrl: './exercise-lti-configuration-dialog.component.html'
})
export class ExerciseLtiConfigurationDialogComponent implements OnInit {

    exercise: Exercise;
    ltiConfiguration: LtiConfiguration;

    constructor(
        public activeModal: NgbActiveModal,
    ) {
    }

    ngOnInit() {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }
}

@Component({
    selector: 'jhi-exercise-lti-configuration-popup',
    template: ''
})
export class ExerciseLtiConfigurationPopupComponent implements OnInit, OnDestroy {

    routeSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        private exercisePopupService: ExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.exercisePopupService
                .open(ExerciseLtiConfigurationDialogComponent as Component, params['id'], true);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
