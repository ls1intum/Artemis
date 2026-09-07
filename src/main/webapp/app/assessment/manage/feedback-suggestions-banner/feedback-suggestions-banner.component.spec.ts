import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackSuggestionsBannerComponent, feedbackSuggestionsNotice } from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';
import { Message } from 'primeng/message';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

describe('FeedbackSuggestionsBannerComponent', () => {
    let fixture: ComponentFixture<FeedbackSuggestionsBannerComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeedbackSuggestionsBannerComponent);
                fixture.componentRef.setInput('isLoading', false);
                fixture.componentRef.setInput('hasAutomaticFeedback', false);
                fixture.componentRef.setInput('isAssessor', false);
                fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', false);
            });
    });

    it('should show the non-AI automatic assessment banner when feedback suggestions are disabled', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.detectChanges();

        const messages = fixture.debugElement.queryAll(By.directive(Message));
        expect(messages).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.automaticAssessmentAvailable"]'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should show the generative AI assessment banner when feedback suggestions are enabled', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        const messages = fixture.debugElement.queryAll(By.directive(Message));
        expect(messages).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should show the loading spinner when loading feedback suggestions', () => {
        fixture.componentRef.setInput('isLoading', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(Message))).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeTruthy();
    });

    it('should render nothing when no conditions are met', () => {
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(Message))).toHaveLength(0);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should keep the inline band appearance by default, with no chrome markup or host class', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        expect(fixture.componentInstance.appearance()).toBe('banner');
        expect(fixture.debugElement.nativeElement.classList.contains('feedback-suggestions-banner--chrome')).toBe(false);
        expect(fixture.debugElement.query(By.css('.feedback-suggestions-chrome'))).toBeNull();
        expect(fixture.debugElement.queryAll(By.directive(Message))).toHaveLength(1);
    });

    describe('chrome appearance', () => {
        const island = () => fixture.debugElement.query(By.css('.feedback-suggestions-chrome'));

        beforeEach(() => {
            fixture.componentRef.setInput('appearance', 'chrome');
        });

        it('should mark the host so only the chrome appearance picks up the island styles', () => {
            fixture.detectChanges();

            expect(fixture.debugElement.nativeElement.classList.contains('feedback-suggestions-banner--chrome')).toBe(true);
        });

        it.each([
            {
                name: 'the AI suggestion island',
                inputs: { hasAutomaticFeedback: true, isAssessor: true, isFeedbackSuggestionsEnabled: true },
                notice: 'suggestions',
                labelKey: 'artemisApp.assessment.feedbackSuggestions.chrome.suggestions',
                hasInfoButton: true,
            },
            {
                name: 'the non-AI automatic assessment island',
                inputs: { hasAutomaticFeedback: true, isAssessor: true, isFeedbackSuggestionsEnabled: false },
                notice: 'automaticAssessment',
                labelKey: 'artemisApp.assessment.feedbackSuggestions.chrome.automaticAssessment',
                hasInfoButton: true,
            },
            {
                name: 'the loading island',
                inputs: { isLoading: true, isFeedbackSuggestionsEnabled: true },
                notice: 'loading',
                labelKey: 'artemisApp.assessment.feedbackSuggestions.chrome.loading',
                hasInfoButton: false,
            },
        ])('should render $name', ({ inputs, notice, labelKey, hasInfoButton }) => {
            for (const [key, value] of Object.entries(inputs)) {
                fixture.componentRef.setInput(key, value);
            }
            fixture.detectChanges();

            expect(island()).toBeTruthy();
            expect(island().nativeElement.getAttribute('data-notice')).toBe(notice);
            expect(island().nativeElement.getAttribute('role')).toBe('status');
            const label = fixture.debugElement.query(By.css('.feedback-suggestions-chrome__label'));
            expect(label.injector.get(TranslateDirective).jhiTranslate()).toBe(labelKey);
            expect(!!fixture.debugElement.query(By.css('[data-testid="feedback-suggestions-chrome-info"]'))).toBe(hasInfoButton);
            expect(fixture.debugElement.queryAll(By.directive(Message))).toHaveLength(0);
        });

        it('should render nothing when there is no notice', () => {
            fixture.detectChanges();

            expect(island()).toBeNull();
            expect(fixture.debugElement.queryAll(By.directive(Message))).toHaveLength(0);
        });
    });
});

describe('feedbackSuggestionsNotice', () => {
    const base = { isLoading: false, hasAutomaticFeedback: true, isAssessor: true, isFeedbackSuggestionsEnabled: false };

    it.each([
        { name: 'suggestions once Athena is enabled', state: { ...base, isFeedbackSuggestionsEnabled: true }, expected: 'suggestions' },
        { name: 'the automatic assessment notice without Athena', state: base, expected: 'automaticAssessment' },
        { name: 'loading while Athena is being queried', state: { ...base, isLoading: true, isFeedbackSuggestionsEnabled: true }, expected: 'loading' },
        { name: 'nothing while loading without Athena', state: { ...base, isLoading: true }, expected: undefined },
        { name: 'nothing without automatic feedback', state: { ...base, hasAutomaticFeedback: false }, expected: undefined },
        { name: 'nothing for a non-assessor', state: { ...base, isAssessor: false }, expected: undefined },
        { name: 'nothing once the assessment is submitted', state: { ...base, resultCompletionDate: dayjs() }, expected: undefined },
    ])('should resolve $name', ({ state, expected }) => {
        expect(feedbackSuggestionsNotice(state)).toBe(expected);
    });
});
