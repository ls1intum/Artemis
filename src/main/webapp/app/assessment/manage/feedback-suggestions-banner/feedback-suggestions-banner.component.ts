import { Component, computed, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle, faRobot, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Message } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type FeedbackSuggestionsNotice = 'automaticAssessment' | 'suggestions' | 'loading';

export interface FeedbackSuggestionsNoticeState {
    isLoading: boolean;
    hasAutomaticFeedback: boolean;
    isAssessor: boolean;
    resultCompletionDate?: dayjs.Dayjs;
    isFeedbackSuggestionsEnabled: boolean;
}

/** Resolves visibility before a host reserves space for the notice. */
export function feedbackSuggestionsNotice(state: FeedbackSuggestionsNoticeState): FeedbackSuggestionsNotice | undefined {
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
    imports: [Message, TooltipModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
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
    readonly appearance = input<FeedbackSuggestionsBannerAppearance>('banner');

    protected readonly notice = computed(() =>
        feedbackSuggestionsNotice({
            isLoading: this.isLoading(),
            hasAutomaticFeedback: this.hasAutomaticFeedback(),
            isAssessor: this.isAssessor(),
            resultCompletionDate: this.resultCompletionDate(),
            isFeedbackSuggestionsEnabled: this.isFeedbackSuggestionsEnabled(),
        }),
    );

    protected readonly chromeIcon = computed(() => (this.notice() === 'suggestions' ? faWandMagicSparkles : faRobot));
    protected readonly chromeLabelKey = computed(() => `artemisApp.assessment.feedbackSuggestions.chrome.${this.notice()}`);

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faQuestionCircle = faQuestionCircle;
}
