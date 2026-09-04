import { Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faInfoCircle, faQuestionCircle, faRobot, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { TooltipModule } from 'primeng/tooltip';
import { TumUiButtonComponent, TumUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type FeedbackSuggestionsNotice = 'automaticAssessment' | 'suggestions' | 'loading' | 'optInRequired';

export interface FeedbackSuggestionsNoticeState {
    isLoading: boolean;
    hasAutomaticFeedback: boolean;
    isAssessor: boolean;
    resultCompletionDate?: dayjs.Dayjs;
    isFeedbackSuggestionsEnabled: boolean;
    requiresAiExperienceOptIn?: boolean;
}

/** Resolves visibility before a host reserves space for the notice. */
export function feedbackSuggestionsNotice(state: FeedbackSuggestionsNoticeState): FeedbackSuggestionsNotice | undefined {
    if (state.requiresAiExperienceOptIn && state.isAssessor && !state.resultCompletionDate) {
        return 'optInRequired';
    }
    if (state.isLoading) {
        return state.isFeedbackSuggestionsEnabled ? 'loading' : undefined;
    }
    if (!state.hasAutomaticFeedback || !state.isAssessor || state.resultCompletionDate) {
        return undefined;
    }
    return state.isFeedbackSuggestionsEnabled ? 'suggestions' : 'automaticAssessment';
}

export type FeedbackSuggestionsBannerAppearance = 'banner' | 'chrome';

@Component({
    selector: 'jhi-feedback-suggestions-banner',
    templateUrl: './feedback-suggestions-banner.component.html',
    styleUrls: ['./feedback-suggestions-banner.component.scss'],
    imports: [TumUiMessageComponent, TumUiButtonComponent, FaIconComponent, TooltipModule, TranslateDirective, ArtemisTranslatePipe],
    host: {
        '[class.feedback-suggestions-banner--chrome]': "appearance() === 'chrome'",
    },
})
export class FeedbackSuggestionsBannerComponent {
    readonly isLoading = input.required<boolean>();
    readonly hasAutomaticFeedback = input.required<boolean>();
    readonly isAssessor = input.required<boolean>();
    readonly resultCompletionDate = input<dayjs.Dayjs | undefined>(undefined);
    readonly isFeedbackSuggestionsEnabled = input.required<boolean>();
    readonly requiresAiExperienceOptIn = input<boolean>(false);
    readonly appearance = input<FeedbackSuggestionsBannerAppearance>('banner');
    readonly optIn = output<void>();

    protected readonly notice = computed(() =>
        feedbackSuggestionsNotice({
            isLoading: this.isLoading(),
            hasAutomaticFeedback: this.hasAutomaticFeedback(),
            isAssessor: this.isAssessor(),
            resultCompletionDate: this.resultCompletionDate(),
            isFeedbackSuggestionsEnabled: this.isFeedbackSuggestionsEnabled(),
            requiresAiExperienceOptIn: this.requiresAiExperienceOptIn(),
        }),
    );

    protected readonly chromeIcon = computed(() => {
        switch (this.notice()) {
            case 'suggestions':
                return faWandMagicSparkles;
            case 'optInRequired':
                return faInfoCircle;
            default:
                return faRobot;
        }
    });
    protected readonly chromeLabelKey = computed(() => `artemisApp.assessment.feedbackSuggestions.chrome.${this.notice()}`);

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faQuestionCircle = faQuestionCircle;
}
