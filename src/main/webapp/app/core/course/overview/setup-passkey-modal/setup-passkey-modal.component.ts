import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faCog } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-setup-passkey-modal',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FontAwesomeModule],
    templateUrl: './setup-passkey-modal.component.html',
    styleUrl: './setup-passkey-modal.component.scss',
})
export class SetupPasskeyModalComponent {
    protected readonly faCog = faCog;

    private activeModal = inject(NgbActiveModal);

    navigateToSetupPasskey() {
        // TODO
    }

    closeModal(): void {
        this.activeModal.close();
    }
}
