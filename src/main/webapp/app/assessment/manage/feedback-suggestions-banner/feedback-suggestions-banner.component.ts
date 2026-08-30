import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
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
    readonly requiresAiExperienceOptIn = input.required<boolean>();
    readonly optIn = output<void>();

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faInfoCircle = faInfoCircle;
}
