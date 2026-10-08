import { Component, computed, input, linkedSignal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';

export type NonEmptyArray<T> = readonly [T, ...T[]];

@Component({
    selector: 'jhi-code-button-popover-content',
    templateUrl: './code-button-popover-content.component.html',
    imports: [TranslateDirective, FaIconComponent, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective],
})
export class CodeButtonPopoverContentComponent {
    protected readonly faCaretDown = faCaretDown;

    readonly authenticationMechanisms = input.required<NonEmptyArray<RepositoryAuthenticationMethod>>();
    protected readonly selectedAuthenticationMechanism = linkedSignal(() => this.authenticationMechanisms()[0]);
    protected readonly selectedAuthenticationMechanismLabel = computed(() => this.getAuthenticationMechanismLabel(this.selectedAuthenticationMechanism()));
    protected readonly authenticationMechanismsWithLabels = computed(() => this.getAuthenticationMechanismsWithLabels());

    private getAuthenticationMechanismsWithLabels() {
        return this.authenticationMechanisms().map((authenticationMechanism) => ({
            authenticationMechanism,
            label: this.getAuthenticationMechanismLabel(authenticationMechanism),
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
