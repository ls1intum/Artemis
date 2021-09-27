import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { TextExamSummaryComponent } from 'app/exam/participate/summary/exercises/text-exam-summary/text-exam-summary.component';
import { TextSubmission } from 'app/entities/text-submission.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextExamSummaryComponent', () => {
    let fixture: ComponentFixture<TextExamSummaryComponent>;
    let component: TextExamSummaryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({ declarations: [TextExamSummaryComponent] })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextExamSummaryComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(fixture.debugElement.nativeElement.querySelector('div').innerHTML).to.equal('No submission');
    });

    it('should display the submission text', () => {
        const submissionText = 'A test submission text';
        component.submission = { text: submissionText } as TextSubmission;
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(fixture.debugElement.nativeElement.querySelector('div').innerHTML).to.equal(submissionText);
    });
});
