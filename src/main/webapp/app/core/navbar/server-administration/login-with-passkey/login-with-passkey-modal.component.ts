import { Component, inject, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { EventManager } from 'app/shared/service/event-manager.service';
import { Router } from '@angular/router';
import { PasskeyPromptComponent } from 'app/core/auth/passkey-prompt/passkey-prompt.component';

@Component({
    selector: 'jhi-login-with-passkey-modal',
    imports: [DialogModule, ButtonComponent, PasskeyPromptComponent],
    templateUrl: './login-with-passkey-modal.component.html',
})
export class LoginWithPasskeyModalComponent {
    protected readonly ButtonType = ButtonType;

    private readonly router = inject(Router);
    private readonly eventManager = inject(EventManager);

    justLoggedInWithPasskey = output<boolean>();

    showModal: boolean = false;

    cancel() {
        this.showModal = false;
    }

    handleLoginSuccess() {
        this.justLoggedInWithPasskey.emit(true);
        this.showModal = false;

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });
    }
}
