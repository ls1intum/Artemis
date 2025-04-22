// learning-path-activation.component.ts
import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faNetworkWired } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-feature-activation',
    templateUrl: './feature-activation.component.html',
    styleUrls: ['./feature-activation.component.scss'],
    imports: [ButtonComponent, TranslateDirective, FaIconComponent],
})
export class FeatureActivationComponent {
    protected readonly faNetworkWired = faNetworkWired;
    headerTitle = input.required<string>();
    description = input.required<string>();
    advantagesTitle = input.required<string>();
    advantages = input.required<string[]>();
    isLoading = input.required<boolean>();
    activateButtonText = input.required<string>();
    activatingButtonText = input.required<string>();

    enable = output<void>();
}
