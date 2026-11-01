import { Component, computed, inject, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faInfoCircle, faPenSquare } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TumAetUiButtonComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-feedback-suggestions-banner',
    templateUrl: './feedback-suggestions-banner.component.html',
    imports: [TumAetUiMessageComponent, TumAetUiButtonComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class FeedbackSuggestionsBannerComponent {
    private readonly profileService = inject(ProfileService);

    readonly isLoading = input.required<boolean>();
    readonly hasAutomaticFeedback = input.required<boolean>();
    readonly isAssessor = input.required<boolean>();
    readonly resultCompletionDate = input<dayjs.Dayjs | undefined>(undefined);
    readonly isFeedbackSuggestionsEnabled = input.required<boolean>();
    readonly requiresAiExperienceOptIn = input<boolean>(false);
    /** Distinguishes a deliberate No AI choice from no choice yet, so the opt-in prompt can say "change" instead of "choose". */
    readonly hasChosenNoAi = input<boolean>(false);
    /** Whether suggestions can still be added to the current assessment, i.e. the assessor has not added feedback yet. */
    readonly canApplyFeedbackSuggestions = input<boolean>(true);
    readonly optIn = output<void>();

    /** Names On-premise only when the AI Experience selection offers it, which requires a local LLM deployment. */
    protected readonly optInHintKey = computed(() => {
        const key = this.hasChosenNoAi() ? 'aiExperienceOptInHintNoAi' : 'aiExperienceOptInHint';
        return `artemisApp.assessment.feedbackSuggestions.${key}${this.profileService.isLLMDeploymentEnabled() ? '' : 'CloudOnly'}`;
    });
    protected readonly optInActionKey = computed(() =>
        this.hasChosenNoAi() ? 'artemisApp.assessment.feedbackSuggestions.changeAiExperience' : 'artemisApp.assessment.feedbackSuggestions.chooseAiExperience',
    );

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faPenSquare = faPenSquare;
}
