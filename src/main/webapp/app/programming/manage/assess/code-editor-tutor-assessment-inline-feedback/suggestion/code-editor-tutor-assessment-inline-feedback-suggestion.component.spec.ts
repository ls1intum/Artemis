import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Course } from 'app/course/shared/entities/course.model';
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-inline-feedback/suggestion/code-editor-tutor-assessment-inline-feedback-suggestion.component';
import { MockComponent } from 'ng-mocks';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent>;

    let feedback: Feedback;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent, FaIconComponent, MockComponent(FeedbackSuggestionBadgeComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent);
        component = fixture.componentInstance;
        feedback = new Feedback();
        fixture.componentRef.setInput('feedback', feedback);
        fixture.componentRef.setInput('course', new Course());
        fixture.componentRef.setInput('codeLine', 1);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should emit onAcceptSuggestion event when Accept button is clicked', () => {
        const emitSpy = vi.fn();
        component.onAcceptSuggestion.subscribe(emitSpy);
        const acceptButton = fixture.debugElement.query(By.css('.btn-success')).nativeElement;
        acceptButton.click();
        expect(emitSpy).toHaveBeenCalledWith(component.feedback());
    });

    it('should emit onDiscardSuggestion event when Discard button is clicked', () => {
        const emitSpy = vi.fn();
        component.onDiscardSuggestion.subscribe(emitSpy);
        const discardButton = fixture.debugElement.query(By.css('.btn-danger')).nativeElement;
        discardButton.click();
        expect(emitSpy).toHaveBeenCalledWith(component.feedback());
    });
});
