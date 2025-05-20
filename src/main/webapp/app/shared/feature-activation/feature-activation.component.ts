import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-feature-activation',
    templateUrl: './feature-activation.component.html',
    styleUrls: ['./feature-activation.component.scss'],
    imports: [ButtonComponent, TranslateDirective, FaIconComponent],
})
export class FeatureActivationComponent {
    protected readonly ButtonType = ButtonType;
    headerTitle = input.required<string>();
    description = input.required<string>();
    headerIcon = input<IconProp>();
    isLoading = input<boolean>(false);
    activateButtonText = input.required<string>();
    secondActivateButtonText = input<string>();

    enable = output<void>();
    enableWithSecondButton = output<void>();
}
