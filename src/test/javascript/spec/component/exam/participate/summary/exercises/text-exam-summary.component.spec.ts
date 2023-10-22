import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextExamSummaryComponent } from 'app/exam/participate/summary/exercises/text-exam-summary/text-exam-summary.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { MockComponent } from 'ng-mocks';
import { By } from '@angular/platform-browser'; // Import By

describe('TextExamSummaryComponent', () => {
    let fixture: ComponentFixture<TextExamSummaryComponent>;
    let component: TextExamSummaryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({ declarations: [TextExamSummaryComponent, MockComponent(TextEditorComponent)] })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextExamSummaryComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(fixture.debugElement.nativeElement.querySelector('div').innerHTML).toContain('No submission');
    });

    it('should display the submission text', () => {
        const submissionText = 'A test submission text';
        component.submission = { text: submissionText } as TextSubmission;
        component.exercise = { studentParticipations: [{ id: 1 }] } as Exercise;
        fixture.detectChanges();

        const textEditorComponent = fixture.debugElement.query(By.directive(TextEditorComponent)).componentInstance;
        expect(textEditorComponent).not.toBeNull();
        expect(textEditorComponent.participationId).toBe(1);
        expect(textEditorComponent.inputSubmission.text).toBe(submissionText);
    });
});
