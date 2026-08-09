import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { Feedback, FeedbackSuggestionType } from 'app/assessment/shared/entities/feedback.model';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { faLightbulb, faWandMagicSparkles } from '@fortawesome/free-solid-svg-icons';

describe('FeedbackSuggestionBadgeComponent', () => {
    let component: FeedbackSuggestionBadgeComponent;
    let fixture: ComponentFixture<FeedbackSuggestionBadgeComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackSuggestionBadgeComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackSuggestionBadgeComponent);
        component = fixture.componentInstance;
        const feedback = new Feedback();
        feedback.text = 'Test Feedback';
        fixture.componentRef.setInput('feedback', feedback);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should have the correct text for a SUGGESTED feedback', () => {
        fixture.componentRef.setInput('feedback', new Feedback());
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.SUGGESTED);

        expect(component.text).toBe('artemisApp.assessment.suggestion.suggested');
    });

    it('should have the correct text for an ACCEPTED feedback', () => {
        fixture.componentRef.setInput('feedback', new Feedback());
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.ACCEPTED);

        expect(component.text).toBe('artemisApp.assessment.suggestion.suggested');
    });

    it('should have the correct text for an ADAPTED feedback', () => {
        fixture.componentRef.setInput('feedback', new Feedback());
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.ADAPTED);

        expect(component.text).toBe('artemisApp.assessment.suggestion.adapted');
    });

    it('should have empty text for undefined feedback type', () => {
        fixture.componentRef.setInput('feedback', new Feedback());
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(undefined as any as FeedbackSuggestionType);

        expect(component.text).toBe('');
    });

    it('should default to the overlay variant with the lightbulb icon and no footer host class', () => {
        expect(component.variant()).toBe('overlay');
        expect(component.icon).toBe(faLightbulb);
        expect((fixture.nativeElement as HTMLElement).classList.contains('suggestion-badge-host--footer')).toBe(false);
    });

    it('should switch to the sparkle icon and footer host class for the footer variant', () => {
        fixture.componentRef.setInput('variant', 'footer');
        fixture.detectChanges();

        expect(component.icon).toBe(faWandMagicSparkles);
        expect((fixture.nativeElement as HTMLElement).classList.contains('suggestion-badge-host--footer')).toBe(true);
    });
});
