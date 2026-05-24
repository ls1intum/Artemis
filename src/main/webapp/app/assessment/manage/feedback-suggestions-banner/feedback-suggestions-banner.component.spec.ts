import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FeedbackSuggestionsBannerComponent } from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';
import { Tag } from 'primeng/tag';

describe('FeedbackSuggestionsBannerComponent', () => {
    setupTestBed({ zoneless: true });
    let component: FeedbackSuggestionsBannerComponent;
    let fixture: ComponentFixture<FeedbackSuggestionsBannerComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FeedbackSuggestionsBannerComponent);
                component = fixture.componentInstance;
                fixture.componentRef.setInput('isLoading', false);
                fixture.componentRef.setInput('hasAutomaticFeedback', false);
                fixture.componentRef.setInput('isAssessor', false);
                fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', false);
            });
    });

    it('should show the non-AI automatic assessment tag when feedback suggestions are disabled', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.detectChanges();

        const tags = fixture.debugElement.queryAll(By.directive(Tag));
        expect(tags).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should show the generative AI assessment tag when feedback suggestions are enabled', () => {
        fixture.componentRef.setInput('hasAutomaticFeedback', true);
        fixture.componentRef.setInput('isAssessor', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        const tags = fixture.debugElement.queryAll(By.directive(Tag));
        expect(tags).toHaveLength(1);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });

    it('should show the loading spinner when loading feedback suggestions', () => {
        fixture.componentRef.setInput('isLoading', true);
        fixture.componentRef.setInput('isFeedbackSuggestionsEnabled', true);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(Tag))).toHaveLength(0);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeTruthy();
    });

    it('should render nothing when no conditions are met', () => {
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.directive(Tag))).toHaveLength(0);
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.assessment.feedbackSuggestions.loading"]'))).toBeFalsy();
    });
});
