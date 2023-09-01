import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { EventEmitter } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback-suggestion.component';
import { MockComponent } from 'ng-mocks';
import { FeedbackSuggestionBadgeComponent } from 'app/exercises/shared/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';

describe('CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent', () => {
    let component: CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule],
            declarations: [CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent, MockComponent(FeedbackSuggestionBadgeComponent)],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent);
        component = fixture.componentInstance;
        component.feedback = new Feedback();
        component.course = new Course();
        component.onAcceptSuggestion = new EventEmitter();
        component.onDiscardSuggestion = new EventEmitter();
        fixture.detectChanges();
    });

    it('should emit onAcceptSuggestion event when Accept button is clicked', () => {
        jest.spyOn(component.onAcceptSuggestion, 'emit');
        const acceptButton = fixture.debugElement.query(By.css('.btn-success')).nativeElement;
        acceptButton.click();
        expect(component.onAcceptSuggestion.emit).toHaveBeenCalledWith(component.feedback);
    });

    it('should emit onDiscardSuggestion event when Discard button is clicked', () => {
        jest.spyOn(component.onDiscardSuggestion, 'emit');
        const discardButton = fixture.debugElement.query(By.css('.btn-danger')).nativeElement;
        discardButton.click();
        expect(component.onDiscardSuggestion.emit).toHaveBeenCalledWith(component.feedback);
    });
});
