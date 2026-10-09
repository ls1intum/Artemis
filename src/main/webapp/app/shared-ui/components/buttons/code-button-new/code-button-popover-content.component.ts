import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

export type NonEmptyArray<T> = readonly [T, ...T[]];
export type SshKeyState = { status: 'missing' } | { status: 'configured'; hasExpiredKeys: boolean };

@Component({
    selector: 'jhi-code-button-popover-content',
    templateUrl: './code-button-popover-content.component.html',
    imports: [TranslateDirective, FaIconComponent, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective, TumAetUiMessageComponent],
})
export class CodeButtonPopoverContentComponent {
    private localStorageService = inject(LocalStorageService);

    protected readonly faCaretDown = faCaretDown;
    protected readonly RepositoryAuthenticationMethod = RepositoryAuthenticationMethod;
    protected readonly sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;

    readonly authenticationMechanisms = input.required<NonEmptyArray<RepositoryAuthenticationMethod>>();
    readonly sshKeyState = input.required<SshKeyState>();
    protected readonly selectedAuthenticationMethod = linkedSignal(() => this.authenticationMechanisms()[0]);
    protected readonly selectedAuthenticationMethodLabel = computed(() => this.getAuthenticationMechanismLabel(this.selectedAuthenticationMethod()));
    protected readonly authenticationMethodsAndLabels = computed(() => this.getAuthenticationMechanismsWithLabels());

    selectAuthenticationMethod(authenticationMethod: RepositoryAuthenticationMethod) {
        this.selectedAuthenticationMethod.set(authenticationMethod);
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', authenticationMethod);
    }

    private getAuthenticationMechanismsWithLabels() {
        return this.authenticationMechanisms().map((authenticationMethod) => ({
            authenticationMethod: authenticationMethod,
            label: this.getAuthenticationMechanismLabel(authenticationMethod),
        }));
    }

    private getAuthenticationMechanismLabel(authenticationMechanism: RepositoryAuthenticationMethod): string {
        switch (authenticationMechanism) {
            case RepositoryAuthenticationMethod.Password:
                return 'HTTPS';
            case RepositoryAuthenticationMethod.Token:
                return 'Token';
            case RepositoryAuthenticationMethod.SSH:
                return 'SSH';
        }
    }
}
