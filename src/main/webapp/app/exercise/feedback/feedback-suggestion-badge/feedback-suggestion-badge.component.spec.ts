import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Feedback, FeedbackSuggestionType } from 'app/assessment/shared/entities/feedback.model';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { MockDirective, MockModule } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('FeedbackSuggestionBadgeComponent', () => {
    setupTestBed({ zoneless: true });
    let component: FeedbackSuggestionBadgeComponent;
    let fixture: ComponentFixture<FeedbackSuggestionBadgeComponent>;
    let translateService: TranslateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(TranslateDirective), MockModule(NgbTooltipModule)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                translateService = TestBed.inject(TranslateService);
            });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(FeedbackSuggestionBadgeComponent);
        component = fixture.componentInstance;
        component.feedback = new Feedback();
        component.feedback.text = 'Test Feedback';
        fixture.detectChanges();
    });

    it('should have the correct text and tooltip for a SUGGESTED feedback', () => {
        component.feedback = new Feedback();
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.SUGGESTED);
        vi.spyOn(translateService, 'instant').mockReturnValue('Mocked Tooltip');

        expect(component.text).toBe('artemisApp.assessment.suggestion.suggested');
        expect(component.tooltip).toBe('Mocked Tooltip');
    });

    it('should have the correct text and tooltip for an ACCEPTED feedback', () => {
        component.feedback = new Feedback();
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.ACCEPTED);
        vi.spyOn(translateService, 'instant').mockReturnValue('Mocked Tooltip');

        expect(component.text).toBe('artemisApp.assessment.suggestion.accepted');
        expect(component.tooltip).toBe('Mocked Tooltip');
    });

    it('should have the correct text and tooltip for an ADAPTED feedback', () => {
        component.feedback = new Feedback();
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.ADAPTED);
        vi.spyOn(translateService, 'instant').mockReturnValue('Mocked Tooltip');

        expect(component.text).toBe('artemisApp.assessment.suggestion.adapted');
        expect(component.tooltip).toBe('Mocked Tooltip');
    });

    it('should have empty text and tooltip for undefined feedback type', () => {
        component.feedback = new Feedback();
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(undefined as any as FeedbackSuggestionType);

        expect(component.text).toBe('');
        expect(component.tooltip).toBe('');
    });

    it('should respect the useDefaultText setting', () => {
        component.useDefaultText = true;

        expect(component.text).toBe('artemisApp.assessment.suggestion.default');
        expect(component.tooltip).toBe('artemisApp.assessment.suggestionTitle.default');
    });

    it('should ignore the useDefaultText setting for ADAPTED feedback', () => {
        component.useDefaultText = true;
        component.feedback = new Feedback();
        vi.spyOn(Feedback, 'getFeedbackSuggestionType').mockReturnValue(FeedbackSuggestionType.ADAPTED);
        vi.spyOn(translateService, 'instant').mockReturnValue('Mocked Tooltip');

        expect(component.text).toBe('artemisApp.assessment.suggestion.adapted');
        expect(component.tooltip).toBe('Mocked Tooltip');
    });
});
