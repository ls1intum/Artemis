import { Component, input, output } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/button/button.component';

@Component({
    selector: 'jhi-feature-activation',
    templateUrl: './feature-activation.component.html',
    imports: [TranslateDirective, ButtonComponent],
    styleUrls: ['./feature-activation.component.scss'],
})
export class FeatureActivationComponent {
    title = input.required<string>();
    description = input.required<string>();
    buttonText = input.required<string>();
    activate = output<void>();
    isLoading = input<boolean>(false);
    onActivateClick(): void {
        if (!this.isLoading()) {
            this.activate.emit();
        }
    }
}
