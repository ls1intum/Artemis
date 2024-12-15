import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-lti-initializer-modal',
    templateUrl: './lti-initializer-modal.component.html',
})
export class LtiInitializerModalComponent {
    private activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);

    password: string;
    loginName: string;
    passwordResetLocation = ['account', 'reset', 'request'];

    readAndUnderstood = false;

    /**
     * Closes the dialog, removes the query parameter and shows a helper message
     */
    clear(): void {
        this.alertService.info('artemisApp.lti.startExercise');
        this.router.navigate([], { relativeTo: this.activatedRoute, queryParams: { initialize: null }, queryParamsHandling: 'merge' });
        this.activeModal.dismiss();
    }
}
