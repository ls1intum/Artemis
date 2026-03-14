import { Component, inject, input, model } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { CopyToClipboardButtonComponent } from 'app/shared/components/buttons/copy-to-clipboard-button/copy-to-clipboard-button.component';
import { DialogModule } from 'primeng/dialog';

@Component({
    selector: 'jhi-lti-initializer-modal',
    templateUrl: './lti-initializer-modal.component.html',
    imports: [TranslateDirective, FormsModule, CopyToClipboardButtonComponent, RouterLink, DialogModule],
})
export class LtiInitializerModalComponent {
    private alertService = inject(AlertService);
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);

    readonly visible = model<boolean>(false);
    readonly password = input<string>('');
    readonly loginName = input<string>('');
    passwordResetLocation = ['account', 'reset', 'request'];

    readAndUnderstood = false;

    /**
     * Closes the dialog, removes the query parameter and shows a helper message
     */
    clear(): void {
        this.alertService.info('artemisApp.lti.startExercise');
        this.router.navigate([], { relativeTo: this.activatedRoute, queryParams: { initialize: null }, queryParamsHandling: 'merge' });
        this.visible.set(false);
    }
}
