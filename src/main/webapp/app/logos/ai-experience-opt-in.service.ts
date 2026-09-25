import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/account/user/shared/user.service';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED, isAcceptedLLMSelection } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

/**
 * Shared entry point for prompting the current user to opt into AI usage (their "AI Experience" setting)
 * from a context other than the dedicated user settings page, e.g. a hint shown next to an AI-powered feature.
 */
@Service()
export class AiExperienceOptInService {
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly llmModalService = inject(LLMSelectionModalService);

    /** Whether the current user has already opted into AI usage (cloud or local). */
    hasAcceptedAiUsage(): boolean {
        return isAcceptedLLMSelection(this.accountService.userIdentity()?.selectedLLMUsage);
    }

    /** Whether the current user deliberately chose No AI, as opposed to not having made a choice yet. */
    hasChosenNoAi(): boolean {
        return this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.NO_AI;
    }

    /**
     * Re-reads the current AI Experience choice from the server. `hasAcceptedAiUsage`/`hasChosenNoAi` read a
     * per-tab cached value that another tab's change does not update, so a caller about to act on either (fetching
     * Athena feedback suggestions, sending a feedback request) must call this immediately beforehand to avoid
     * firing a request the server will now reject.
     */
    refreshAiExperience(): Observable<LLMSelectionDecision | undefined> {
        return this.accountService.refreshSelectedLLMUsage();
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
