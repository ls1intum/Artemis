import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';

@Component({
    selector: 'jhi-lti-initializer-modal',
    templateUrl: './lti-initializer-modal.component.html',
    imports: [TranslateDirective, FormsModule, CopyIconButtonComponent, RouterLink],
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
