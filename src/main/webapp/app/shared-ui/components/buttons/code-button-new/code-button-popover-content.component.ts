import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

export type NonEmptyArray<T> = readonly [T, ...T[]];
export type SshKeyState = { status: 'missing' } | { status: 'configured'; hasExpiredKeys: boolean };
export type AuthenticationMethodData =
    | { authenticationMethod: RepositoryAuthenticationMethod.SSH; url: string; sshKeyState: SshKeyState }
    | { authenticationMethod: RepositoryAuthenticationMethod.Token; url: string }
    | { authenticationMethod: RepositoryAuthenticationMethod.Password; url: string };

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

    readonly authenticationMethodData = input.required<NonEmptyArray<AuthenticationMethodData>>();
    protected readonly selectedAuthenticationMethodData = linkedSignal(() => this.initializeSelectedAuthenticationMethodData());
    protected readonly selectedAuthenticationMethodLabel = computed(() => this.getAuthenticationMechanismLabel(this.selectedAuthenticationMethodData().authenticationMethod));
    protected readonly authenticationMethodDataAndLabels = computed(() => this.getAuthenticationMechanismsWithLabels());
    protected readonly repositoryUrl = computed(() => this.selectedAuthenticationMethodData().url);

    selectAuthenticationMethod(authenticationMethodData: AuthenticationMethodData) {
        this.selectedAuthenticationMethodData.set(authenticationMethodData);
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', authenticationMethodData.authenticationMethod);
    }

    private initializeSelectedAuthenticationMethodData(): AuthenticationMethodData {
        const authenticationMethodData = this.authenticationMethodData();
        let initialAuthenticationMethod = this.localStorageService.retrieve<RepositoryAuthenticationMethod>('code-button-state');
        if (initialAuthenticationMethod === RepositoryAuthenticationMethod.Password) {
            initialAuthenticationMethod = RepositoryAuthenticationMethod.Token;
        }
        return authenticationMethodData.find((data) => data.authenticationMethod === initialAuthenticationMethod) ?? authenticationMethodData[0];
    }

    private getAuthenticationMechanismsWithLabels() {
        return this.authenticationMethodData().map((authenticationMethodData) => ({
            authenticationMethodData,
            label: this.getAuthenticationMechanismLabel(authenticationMethodData.authenticationMethod),
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
