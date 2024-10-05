import { Component, Input, inject } from '@angular/core';
import { faLightbulb } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { Feedback, FeedbackSuggestionType } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-feedback-suggestion-badge',
    templateUrl: './feedback-suggestion-badge.component.html',
    styleUrls: ['./feedback-suggestion-badge.component.scss'],
})
export class FeedbackSuggestionBadgeComponent {
    private translateService = inject(TranslateService);

    @Input()
    feedback: Feedback;

    @Input()
    useDefaultText = false;

    // Icons
    faLightbulb = faLightbulb;

    get text(): string {
        const feedbackSuggestionType = Feedback.getFeedbackSuggestionType(this.feedback);
        if (feedbackSuggestionType === FeedbackSuggestionType.ADAPTED) {
            // Always mark adapted feedback suggestions as such, even with the default badge in text mode
            return 'artemisApp.assessment.suggestion.adapted';
        }
        if (this.useDefaultText) {
            return 'artemisApp.assessment.suggestion.default';
        }
        switch (feedbackSuggestionType) {
            case FeedbackSuggestionType.SUGGESTED:
                return 'artemisApp.assessment.suggestion.suggested';
            case FeedbackSuggestionType.ACCEPTED:
                return 'artemisApp.assessment.suggestion.accepted';
            default:
                return '';
        }
    }

    get tooltip(): string {
        if (this.useDefaultText) {
            return this.translateService.instant('artemisApp.assessment.suggestionTitle.default');
        }
        const feedbackSuggestionType = Feedback.getFeedbackSuggestionType(this.feedback);
        switch (feedbackSuggestionType) {
            case FeedbackSuggestionType.SUGGESTED:
                return this.translateService.instant('artemisApp.assessment.suggestionTitle.suggested');
            case FeedbackSuggestionType.ACCEPTED:
                return this.translateService.instant('artemisApp.assessment.suggestionTitle.accepted');
            case FeedbackSuggestionType.ADAPTED:
                return this.translateService.instant('artemisApp.assessment.suggestionTitle.adapted');
            default:
                return '';
        }
    }
}
