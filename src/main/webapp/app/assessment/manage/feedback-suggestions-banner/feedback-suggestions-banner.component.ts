import { Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faInfoCircle, faPenSquare } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TumUiButtonComponent, TumUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-suggestions-banner',
    templateUrl: './feedback-suggestions-banner.component.html',
    imports: [TumUiMessageComponent, TumUiButtonComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class FeedbackSuggestionsBannerComponent {
    readonly isLoading = input.required<boolean>();
    readonly hasAutomaticFeedback = input.required<boolean>();
    readonly isAssessor = input.required<boolean>();
    readonly resultCompletionDate = input<dayjs.Dayjs | undefined>(undefined);
    readonly isFeedbackSuggestionsEnabled = input.required<boolean>();
    readonly requiresAiExperienceOptIn = input<boolean>(false);
    /** Distinguishes a deliberate No AI choice from no choice yet, so the opt-in prompt can say "change" instead of "choose". */
    readonly hasChosenNoAi = input<boolean>(false);
    readonly optIn = output<void>();

    protected readonly optInHintKey = computed(() =>
        this.hasChosenNoAi() ? 'artemisApp.assessment.feedbackSuggestions.aiExperienceOptInHintNoAi' : 'artemisApp.assessment.feedbackSuggestions.aiExperienceOptInHint',
    );
    protected readonly optInActionKey = computed(() =>
        this.hasChosenNoAi() ? 'artemisApp.assessment.feedbackSuggestions.changeAiExperience' : 'artemisApp.assessment.feedbackSuggestions.chooseAiExperience',
    );

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faPenSquare = faPenSquare;
}
