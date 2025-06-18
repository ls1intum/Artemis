import { Component, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-iris-settings',
    imports: [TranslateDirective],
    templateUrl: './iris-settings.component.html',
})
export class IrisSettingsComponent {
    private readonly irisChatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);

    updateExternalLLMUsageConsent(accepted: boolean) {
        this.irisChatService.updateExternalLLMUsageConsent(accepted);
        this.accountService.setUserAcceptedExternalLLMUsage(accepted);
    }
}
