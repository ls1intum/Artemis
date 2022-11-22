import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-lti-initializer-modal',
    templateUrl: './lti-initializer-modal.component.html',
})
export class LtiInitializerModalComponent {
    password: string;
    passwordResetLocation = ['account', 'reset', 'request'];

    readAndUnderstood = false;

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService, private router: Router, private activatedRoute: ActivatedRoute) {}

    /**
     * Closes the dialog, removes the query parameter and shows a helper message
     */
    clear(): void {
        this.alertService.info('artemisApp.lti.startExercise');
        this.router.navigate([], { relativeTo: this.activatedRoute, queryParams: { initialize: null }, queryParamsHandling: 'merge' });
        this.activeModal.dismiss();
    }
}
