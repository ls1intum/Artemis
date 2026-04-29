import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { AlertService } from 'app/shared/service/alert.service';
import dayjs from 'dayjs/esm';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';

@Component({
    selector: 'jhi-llm-usage-settings',
    imports: [TranslateDirective, ArtemisDatePipe],
    templateUrl: './llm-usage-settings.component.html',
})
export class LlmUsageSettingsComponent implements OnInit {
    private readonly userService = inject(UserService);
    private readonly accountService = inject(AccountService);
    private readonly llmModalService = inject(LLMSelectionModalService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    currentLLMSelectionDecision = signal<LLMSelectionDecision | undefined>(undefined);
    currentLLMSelectionDecisionDate = signal<dayjs.Dayjs | undefined>(undefined);

    ngOnInit() {
        this.updateLLMUsageDecision();
    }

    async openSelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open(this.currentLLMSelectionDecision());

        if (choice && choice !== LLM_MODAL_DISMISSED) {
            this.updateLLMSelectionDecision(choice);
        }
    }

    private updateLLMUsageDecision() {
        this.currentLLMSelectionDecision.set(this.accountService.userIdentity()?.selectedLLMUsage !== undefined ? this.accountService.userIdentity()?.selectedLLMUsage : undefined);
        this.currentLLMSelectionDecisionDate.set(
            this.accountService.userIdentity()?.selectedLLMUsageTimestamp !== undefined ? this.accountService.userIdentity()?.selectedLLMUsageTimestamp : undefined,
        );
    }

    updateLLMSelectionDecision(accepted: LLMSelectionDecision) {
        // Persist server-side, then mirror to local account state. The settings page does not need
        // any chat-host concerns (close, reopen, etc.) — it is purely a server+account mirror, so
        // it bypasses the chat controller entirely.
        this.userService
            .updateLLMSelectionDecision(accepted)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.accountService.setUserLLMSelectionDecision(accepted);
                    this.updateLLMUsageDecision();
                },
                error: () => {
                    // Surface failure so the user knows the change did not stick — without this,
                    // the modal closes with no feedback and local state silently drifts from server.
                    this.alertService.error('artemisApp.userSettings.LLMUsageSettingsPage.updateFailed');
                },
            });
    }

    protected readonly LLMSelectionDecision = LLMSelectionDecision;
}
