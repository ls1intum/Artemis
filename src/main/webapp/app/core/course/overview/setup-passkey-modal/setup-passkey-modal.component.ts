import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-setup-passkey-modal',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FontAwesomeModule],
    templateUrl: './setup-passkey-modal.component.html',
})
export class SetupPasskeyModalComponent {
    protected readonly faKey = faKey;
    protected readonly faShieldHalved = faShieldHalved;

    private activeModal = inject(NgbActiveModal);
    private router = inject(Router);

    navigateToSetupPasskey() {
        this.closeModal();
        this.router.navigateByUrl('/user-settings/passkeys');
    }

    closeModal(): void {
        this.activeModal.close();
    }
}
