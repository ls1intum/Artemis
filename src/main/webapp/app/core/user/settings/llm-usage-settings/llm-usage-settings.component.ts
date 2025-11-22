import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

@Component({
    selector: 'jhi-llm-usage-settings',
    imports: [TranslateDirective, ArtemisDatePipe],
    templateUrl: './llm-usage-settings.component.html',
})
export class LlmUsageSettingsComponent implements OnInit {
    private readonly irisChatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);

    currentLLMSelectionDecision = signal<LLMSelectionDecision | undefined>(undefined);
    currentLLMSelectionDecisionDate = signal<dayjs.Dayjs | undefined>(undefined);

    ngOnInit() {
        this.updateLLMUsageDecision();
    }

    private updateLLMUsageDecision() {
        this.currentLLMSelectionDecision.set(this.accountService.userIdentity()?.selectedLLMUsage !== undefined ? this.accountService.userIdentity()?.selectedLLMUsage : undefined);
        this.currentLLMSelectionDecisionDate.set(
            this.accountService.userIdentity()?.selectedLLMUsageTimestamp !== undefined ? this.accountService.userIdentity()?.selectedLLMUsageTimestamp : undefined,
        );
    }

    updateLLMSelectionDecision(accepted: LLMSelectionDecision) {
        this.irisChatService.updateLLMUsageConsent(accepted);
        this.accountService.setUserLLMSelectionDecision(accepted);
        this.updateLLMUsageDecision();
    }

    protected readonly LLMSelectionDecision = LLMSelectionDecision;
}
