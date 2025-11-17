import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-external-llm-usage-settings',
    imports: [TranslateDirective, ArtemisDatePipe],
    templateUrl: './external-llm-usage-settings.component.html',
})
export class ExternalLlmUsageSettingsComponent implements OnInit {
    private readonly irisChatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);

    externalLLMUsageAccepted = signal<dayjs.Dayjs | undefined>(undefined);

    internalLLMUsageAccepted = signal<dayjs.Dayjs | undefined>(undefined);

    ngOnInit() {
        this.updateExternalLLMUsageAccepted();
        this.updateInternalLLMUsageAccepted();
    }

    private updateExternalLLMUsageAccepted() {
        this.externalLLMUsageAccepted.set(
            this.accountService.userIdentity()?.externalLLMUsageAccepted ? dayjs(this.accountService.userIdentity()?.externalLLMUsageAccepted) : undefined,
        );
    }

    private updateInternalLLMUsageAccepted() {
        this.internalLLMUsageAccepted.set(
            this.accountService.userIdentity()?.internalLLMUsageAccepted ? dayjs(this.accountService.userIdentity()?.internalLLMUsageAccepted) : undefined,
        );
    }

    updateExternalLLMUsageConsent(accepted: boolean) {
        this.irisChatService.updateExternalLLMUsageConsent(accepted);
        this.accountService.setUserAcceptedExternalLLMUsage(accepted);
        this.updateExternalLLMUsageAccepted();
    }

    updateInternalLLMUsageConsent(accepted: boolean) {
        this.irisChatService.updateInternalLLMUsageConsent(accepted);
        this.accountService.setUserAcceptedInternalLLMUsage(accepted);
        this.updateInternalLLMUsageAccepted();
    }
}
