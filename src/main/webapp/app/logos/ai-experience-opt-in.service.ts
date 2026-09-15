import { Injectable, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/account/user/shared/user.service';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLM_MODAL_DISMISSED, isAcceptedLLMSelection } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

/**
 * Shared entry point for prompting the current user to opt into AI usage (their "AI Experience" setting)
 * from a context other than the dedicated user settings page, e.g. a hint shown next to an AI-powered feature.
 */
@Injectable({ providedIn: 'root' })
export class AiExperienceOptInService {
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly llmModalService = inject(LLMSelectionModalService);

    /** Whether the current user has already opted into AI usage (cloud or local). */
    hasAcceptedAiUsage(): boolean {
        return isAcceptedLLMSelection(this.accountService.userIdentity()?.selectedLLMUsage);
    }

    /**
     * Opens the AI Experience selection modal and persists the user's choice.
     * Calls `onAccepted` only if the user opted into AI usage (cloud or local); does nothing on NO_AI or dismissal.
     */
    promptForAiUsage(onAccepted: () => void): void {
        void this.llmModalService.open(this.accountService.userIdentity()?.selectedLLMUsage).then((choice) => {
            if (choice === LLM_MODAL_DISMISSED) {
                return;
            }
            this.userService.updateLLMSelectionDecision(choice).subscribe(() => {
                this.accountService.setUserLLMSelectionDecision(choice);
                if (isAcceptedLLMSelection(choice)) {
                    onAccepted();
                }
            });
        });
    }
}
