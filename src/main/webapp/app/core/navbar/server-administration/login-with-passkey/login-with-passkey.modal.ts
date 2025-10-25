import { Component } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-login-with-passkey-modal',
    imports: [DialogModule, TranslateDirective, FaIconComponent, ButtonComponent],
    templateUrl: './login-with-passkey.modal.html',
})
export class LoginWithPasskeyModal {
    protected readonly faKey = faKey;

    showDialog: boolean = false;

    signInWithPasskey() {
        // TODO
    }

    cancel() {
        this.showDialog = false;
    }

    protected readonly ButtonType = ButtonType;
}
