import { Component, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { PasskeyPromptComponent } from 'app/core/auth/passkey-prompt/passkey-prompt.component';

@Component({
    selector: 'jhi-login-with-passkey-modal',
    imports: [DialogModule, ButtonComponent, PasskeyPromptComponent],
    templateUrl: './login-with-passkey-modal.component.html',
})
export class LoginWithPasskeyModalComponent {
    protected readonly ButtonType = ButtonType;

    justLoggedInWithPasskey = output<boolean>();

    showModal: boolean = false;

    cancel() {
        this.showModal = false;
    }

    handleLoginSuccess() {
        this.justLoggedInWithPasskey.emit(true);
        this.showModal = false;
    }
}
