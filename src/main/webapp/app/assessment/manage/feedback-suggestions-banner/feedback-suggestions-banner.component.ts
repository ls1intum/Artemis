import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-suggestions-banner',
    templateUrl: './feedback-suggestions-banner.component.html',
    imports: [MessageModule, TooltipModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class FeedbackSuggestionsBannerComponent {
    readonly isLoading = input.required<boolean>();
    readonly hasAutomaticFeedback = input.required<boolean>();
    readonly isAssessor = input.required<boolean>();
    readonly resultCompletionDate = input<unknown>(undefined);
    readonly isFeedbackSuggestionsEnabled = input.required<boolean>();

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faQuestionCircle = faQuestionCircle;
}
