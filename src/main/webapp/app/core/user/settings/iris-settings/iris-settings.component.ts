import { Component, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';

@Component({
    selector: 'jhi-iris-settings',
    imports: [TranslateDirective],
    templateUrl: './iris-settings.component.html',
    styleUrl: './iris-settings.component.scss',
})
export class IrisSettingsComponent {
    private readonly irisChatService = inject(IrisChatService);

    updateExternalLLMUsageConsent(accepted: boolean) {
        this.irisChatService.updateExternalLLMUsageConsent(accepted);
    }
}
