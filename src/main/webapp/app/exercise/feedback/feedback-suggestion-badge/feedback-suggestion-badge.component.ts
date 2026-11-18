import { Component, input } from '@angular/core';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faLightbulb, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackSuggestionType } from 'app/assessment/shared/entities/feedback.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-feedback-suggestion-badge',
    templateUrl: './feedback-suggestion-badge.component.html',
    styleUrls: ['./feedback-suggestion-badge.component.scss'],
    imports: [FaIconComponent, TranslateDirective],
    host: {
        '[class.suggestion-badge-host--footer]': 'variant() === "footer"',
    },
})
export class FeedbackSuggestionBadgeComponent {
    readonly feedbackText = input<string | undefined>(undefined);

    readonly variant = input<'overlay' | 'footer'>('overlay');

    // Icons
    faLightbulb = faLightbulb;
    faWandMagicSparkles = faWandMagicSparkles;

    get icon(): IconDefinition {
        return this.variant() === 'footer' ? this.faWandMagicSparkles : this.faLightbulb;
    }

    get text(): string {
        switch (Feedback.getFeedbackSuggestionType(this.feedbackText())) {
            case FeedbackSuggestionType.SUGGESTED:
            case FeedbackSuggestionType.ACCEPTED:
                return 'artemisApp.assessment.suggestion.suggested';
            case FeedbackSuggestionType.ADAPTED:
                return 'artemisApp.assessment.suggestion.adapted';
            default:
                return '';
        }
    }
}
