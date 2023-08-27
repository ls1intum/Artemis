import { Component, Input } from '@angular/core';
import { faLightbulb } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { Feedback, FeedbackSuggestionType } from 'app/entities/feedback.model';

@Component({
    selector: 'jhi-feedback-suggestion-badge',
    templateUrl: './feedback-suggestion-badge.component.html',
    styleUrls: ['./feedback-suggestion-badge.component.scss'],
})
export class FeedbackSuggestionBadgeComponent {
    @Input()
    feedback: Feedback;

    // Icons
    faLightbulb = faLightbulb;

    constructor(private translateService: TranslateService) {}

    get text(): string {
        const feedbackSuggestionType = Feedback.getFeedbackSuggestionType(this.feedback);
        switch (feedbackSuggestionType) {
            case FeedbackSuggestionType.SUGGESTED:
                return 'artemisApp.assessment.suggestion.suggested';
            case FeedbackSuggestionType.ACCEPTED:
                return 'artemisApp.assessment.suggestion.accepted';
            case FeedbackSuggestionType.ADAPTED:
                return 'artemisApp.assessment.suggestion.adapted';
            default:
                return '';
        }
    }

    get tooltip(): string {
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
